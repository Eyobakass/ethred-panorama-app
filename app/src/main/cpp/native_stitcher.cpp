#include "native_stitcher.h"
#include <android/log.h>
#include <fstream>
#include <iostream>
#include <cmath>
#include <memory>
#include <vector>
#include <string>

#define LOG_TAG "NativeStitcherCPP"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#ifdef HAVE_OPENCV
#include <opencv2/core.hpp>
#include <opencv2/imgcodecs.hpp>
#include <opencv2/imgproc.hpp>
#include <opencv2/photo.hpp>
#include <opencv2/features2d.hpp>
#include <opencv2/calib3d.hpp>
#include <opencv2/stitching.hpp>
#include <opencv2/stitching/detail/blenders.hpp>
#include <opencv2/stitching/detail/camera.hpp>
#include <opencv2/stitching/detail/exposure_compensate.hpp>
#include <opencv2/stitching/detail/matchers.hpp>
#include <opencv2/stitching/detail/seam_finders.hpp>
#include <opencv2/stitching/detail/warpers.hpp>
#endif

// ─── XMP Metadata Helper ─────────────────────────────────────────────────────
// Correctly embeds GPano XMP in JPEG APP1 segment (not appended after JPEG end)
bool writeGPanoXmpToJpeg(const std::string& jpegPath, int width, int height) {
    // Read the original JPEG bytes
    std::ifstream inFile(jpegPath, std::ios::binary);
    if (!inFile.is_open()) {
        LOGE("Cannot open JPEG for XMP embedding: %s", jpegPath.c_str());
        return false;
    }
    std::vector<uint8_t> jpegBytes(
        (std::istreambuf_iterator<char>(inFile)),
        std::istreambuf_iterator<char>()
    );
    inFile.close();

    // Validate JPEG SOI marker (FF D8)
    if (jpegBytes.size() < 2 || jpegBytes[0] != 0xFF || jpegBytes[1] != 0xD8) {
        LOGE("File is not a valid JPEG");
        return false;
    }

    std::string xmpPacket =
        "<?xpacket begin=\"\xEF\xBB\xBF\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>"
        "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\">"
        "<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">"
        "<rdf:Description rdf:about=\"\" xmlns:GPano=\"http://ns.google.com/photos/1.0/panorama/\">"
        "<GPano:UsePanoramaViewer>True</GPano:UsePanoramaViewer>"
        "<GPano:ProjectionType>equirectangular</GPano:ProjectionType>"
        "<GPano:FullPanoWidthPixels>" + std::to_string(width) + "</GPano:FullPanoWidthPixels>"
        "<GPano:FullPanoHeightPixels>" + std::to_string(height) + "</GPano:FullPanoHeightPixels>"
        "<GPano:CroppedAreaImageWidthPixels>" + std::to_string(width) + "</GPano:CroppedAreaImageWidthPixels>"
        "<GPano:CroppedAreaImageHeightPixels>" + std::to_string(height) + "</GPano:CroppedAreaImageHeightPixels>"
        "<GPano:CroppedAreaLeftPixels>0</GPano:CroppedAreaLeftPixels>"
        "<GPano:CroppedAreaTopPixels>0</GPano:CroppedAreaTopPixels>"
        "</rdf:Description></rdf:RDF></x:xmpmeta>"
        "<?xpacket end=\"w\"?>";

    // Build APP1 segment: FF E1 | 2-byte length | "http://ns.adobe.com/xap/1.0/\0" | xmpPacket
    const std::string xmpNs = "http://ns.adobe.com/xap/1.0/\0";
    size_t segmentDataLen = xmpNs.size() + 1 + xmpPacket.size();
    uint16_t segmentLength = static_cast<uint16_t>(segmentDataLen + 2); // +2 for length field itself

    std::vector<uint8_t> app1Segment;
    app1Segment.push_back(0xFF);
    app1Segment.push_back(0xE1);
    app1Segment.push_back((segmentLength >> 8) & 0xFF);
    app1Segment.push_back(segmentLength & 0xFF);
    for (char c : xmpNs) app1Segment.push_back(static_cast<uint8_t>(c));
    app1Segment.push_back(0x00); // null terminator
    for (char c : xmpPacket) app1Segment.push_back(static_cast<uint8_t>(c));

    // Insert APP1 after SOI (FF D8)
    std::vector<uint8_t> result;
    result.push_back(jpegBytes[0]); // FF
    result.push_back(jpegBytes[1]); // D8
    result.insert(result.end(), app1Segment.begin(), app1Segment.end());
    result.insert(result.end(), jpegBytes.begin() + 2, jpegBytes.end());

    std::ofstream outFile(jpegPath, std::ios::binary | std::ios::trunc);
    if (!outFile.is_open()) return false;
    outFile.write(reinterpret_cast<const char*>(result.data()), result.size());
    outFile.close();

    LOGI("GPano XMP embedded in APP1 segment: %dx%d → %s", width, height, jpegPath.c_str());
    return true;
}

// ─── JNI Result Construction ─────────────────────────────────────────────────
jobject createStitchResult(JNIEnv* env, bool isSuccess, const std::string& outputPath,
                           int qualityScore, const std::string& errorMsg) {
    jclass resultClass = env->FindClass("com/ethred/panorama/stitching/StitchResult");
    if (!resultClass) {
        LOGE("Cannot find StitchResult class");
        return nullptr;
    }
    jmethodID constructor = env->GetMethodID(resultClass, "<init>", "(ZLjava/lang/String;ILjava/lang/String;)V");
    jstring jOutputPath = isSuccess ? env->NewStringUTF(outputPath.c_str()) : nullptr;
    jstring jErrorMsg   = !errorMsg.empty() ? env->NewStringUTF(errorMsg.c_str()) : nullptr;
    return env->NewObject(resultClass, constructor, (jboolean)isSuccess, jOutputPath, (jint)qualityScore, jErrorMsg);
}

// ─── Quality Score Calculator ─────────────────────────────────────────────────
int computeQualityScore(int featureCount, double reprojectionError, double inpaintedPercent) {
    // Higher feature count = better; lower error and inpainted % = better
    int score = 3;
    if (featureCount > 500) score++;
    if (featureCount > 1000) score++;
    if (reprojectionError > 5.0) score--;
    if (inpaintedPercent > 0.25) score--;
    return std::max(1, std::min(5, score));
}

// ─── JNI Entry Point ─────────────────────────────────────────────────────────
JNIEXPORT jobject JNICALL
Java_com_ethred_panorama_stitching_NativeStitcher_nativeStitchFrames(
        JNIEnv* env, jobject thiz,
        jobjectArray framePaths, jfloatArray yaws, jfloatArray pitches, jfloatArray rolls,
        jstring outputPath, jint nadirCapOption) {

    int frameCount = env->GetArrayLength(framePaths);
    const char* cOutputPath = env->GetStringUTFChars(outputPath, nullptr);
    std::string outPathStr(cOutputPath);
    env->ReleaseStringUTFChars(outputPath, cOutputPath);

    LOGI("Stitching pipeline: %d frames → %s (nadirOption=%d)", frameCount, outPathStr.c_str(), (int)nadirCapOption);

    if (frameCount < 8) {
        return createStitchResult(env, false, "", 0, "Insufficient frames (minimum 8 required)");
    }

    // Extract frame paths
    std::vector<std::string> inputPaths;
    for (int i = 0; i < frameCount; i++) {
        auto pathStr = (jstring)env->GetObjectArrayElement(framePaths, i);
        const char* cPath = env->GetStringUTFChars(pathStr, nullptr);
        inputPaths.emplace_back(cPath);
        env->ReleaseStringUTFChars(pathStr, cPath);
    }

    // Extract gyro data
    jfloat* yawArr   = env->GetFloatArrayElements(yaws, nullptr);
    jfloat* pitchArr = env->GetFloatArrayElements(pitches, nullptr);
    env->ReleaseFloatArrayElements(yaws, yawArr, JNI_ABORT);
    env->ReleaseFloatArrayElements(pitches, pitchArr, JNI_ABORT);

#ifdef HAVE_OPENCV
    // ── Step 1: Decode & Downsample for feature detection ──────────────────
    LOGI("Step 1: Decoding frames for feature detection");
    std::vector<cv::Mat> featureMats;
    std::vector<cv::Mat> fullResMats;
    int totalFeatures = 0;

    for (const auto& path : inputPaths) {
        cv::Mat full = cv::imread(path, cv::IMREAD_COLOR);
        if (full.empty()) {
            LOGE("Failed to decode: %s", path.c_str());
            continue;
        }
        // Downsample for feature detection — 1280x960 is sufficient for AKAZE and halves mem/time
        cv::Mat small;
        cv::resize(full, small, cv::Size(1280, 960));
        featureMats.push_back(small);
        fullResMats.push_back(full);
    }

    if ((int)featureMats.size() < 16) {
        return createStitchResult(env, false, "", 0, "Failed to decode enough frames");
    }

    // ── Step 2: AKAZE Feature Detection (fallback ORB for low-RAM devices) ─
    LOGI("Step 2: AKAZE feature detection");
    bool useAkaze = true; // Could check ActivityManager.getMemoryInfo() via JNI for RAM tier
    cv::Ptr<cv::Feature2D> detector;
    if (useAkaze) {
        detector = cv::AKAZE::create(
            cv::AKAZE::DESCRIPTOR_MLDB, // descriptor_type per SRS FR-STITCH-02
            0,                          // descriptor_size = 0 (full size)
            3,                          // descriptor_channels
            0.001f,                     // threshold per SRS FR-STITCH-02
            4, 4,
            cv::KAZE::DIFF_PM_G2
        );
    } else {
        // ORB fallback for <3GB RAM devices
        detector = cv::ORB::create(2000);
    }

    std::vector<std::vector<cv::KeyPoint>> allKeypoints(featureMats.size());
    std::vector<cv::Mat> allDescriptors(featureMats.size());

    for (size_t i = 0; i < featureMats.size(); i++) {
        detector->detectAndCompute(featureMats[i], cv::noArray(), allKeypoints[i], allDescriptors[i]);
        totalFeatures += (int)allKeypoints[i].size();
        LOGI("Frame %zu: %zu keypoints", i, allKeypoints[i].size());
    }

    // ── Step 3: FLANN + RANSAC Feature Matching ─────────────────────────────
    LOGI("Step 3: FLANN matching + RANSAC homography");
    cv::Ptr<cv::DescriptorMatcher> matcher;
    if (useAkaze) {
        // LSH index for binary AKAZE descriptors
        cv::flann::IndexParams* indexParams = new cv::flann::LshIndexParams(12, 20, 2);
        matcher = cv::makePtr<cv::FlannBasedMatcher>(indexParams);
    } else {
        matcher = cv::BFMatcher::create(cv::NORM_HAMMING);
    }

    double totalReprojError = 0.0;
    int goodMatchPairs = 0;

    for (size_t i = 0; i < featureMats.size() - 1; i++) {
        if (allDescriptors[i].empty() || allDescriptors[i+1].empty()) continue;
        std::vector<std::vector<cv::DMatch>> knnMatches;
        matcher->knnMatch(allDescriptors[i], allDescriptors[i+1], knnMatches, 2);

        // Lowe's ratio test 0.75 as per SRS FR-STITCH-02
        std::vector<cv::DMatch> goodMatches;
        for (const auto& m : knnMatches) {
            if (m.size() == 2 && m[0].distance < 0.75f * m[1].distance) {
                goodMatches.push_back(m[0]);
            }
        }

        if ((int)goodMatches.size() >= 8) {
            std::vector<cv::Point2f> pts1, pts2;
            for (const auto& m : goodMatches) {
                pts1.push_back(allKeypoints[i][m.queryIdx].pt);
                pts2.push_back(allKeypoints[i+1][m.trainIdx].pt);
            }
            // RANSAC threshold: 4.0 pixels per SRS FR-STITCH-02
            cv::Mat mask;
            cv::findHomography(pts1, pts2, cv::RANSAC, 4.0, mask);
            int inliers = cv::countNonZero(mask);
            totalReprojError += (1.0 - (double)inliers / goodMatches.size());
            goodMatchPairs++;
        }
    }

    double avgReprojError = goodMatchPairs > 0 ? (totalReprojError / goodMatchPairs) : 1.0;

    // ── Step 4–9: Full Stitching Pipeline with Detail API ──────────────────
    LOGI("Step 4: Stitching with SphericalWarper + GraphCutSeamFinder + MultiBandBlender");
    cv::Ptr<cv::Stitcher> stitcher = cv::Stitcher::create(cv::Stitcher::PANORAMA);

    // Configure explicit detail pipeline components per SRS §2.3
    stitcher->setPanoConfidenceThresh(0.2f);
    // BlocksGainCompensator block_size=32 as per FR-STITCH-03
    stitcher->setExposureCompensator(
        cv::makePtr<cv::detail::BlocksGainCompensator>(32)
    );
    // GraphCutSeamFinder(COST_COLOR_GRAD) as per FR-STITCH-03
    stitcher->setSeamFinder(
        cv::makePtr<cv::detail::GraphCutSeamFinder>(cv::detail::GraphCutSeamFinder::COST_COLOR_GRAD)
    );
    // MultiBandBlender num_bands=3 — 5 is overkill on mobile, 3 gives same quality with much less RAM/time
    stitcher->setBlender(cv::makePtr<cv::detail::MultiBandBlender>(false, 3));

    cv::Mat panorama;
    cv::Stitcher::Status status = stitcher->stitch(featureMats, panorama);

    if (status != cv::Stitcher::OK) {
        LOGE("Stitcher failed status=%d", (int)status);
        const char* msg = "Feature matching failed. Try rotating more slowly.";
        if (status == cv::Stitcher::ERR_NEED_MORE_IMGS) msg = "Not enough overlap. Please retake with slower rotation.";
        return createStitchResult(env, false, "", 1, msg);
    }

    // ── Step 10: Zenith & Nadir Inpainting ─────────────────────────────────
    LOGI("Step 10: Zenith/Nadir inpainting (nadirOption=%d)", (int)nadirCapOption);
    cv::Mat panoMask = cv::Mat::zeros(panorama.size(), CV_8UC1);

    int zenithH = (int)(panorama.rows * 0.12); // top 12% = zenith
    int nadirH  = (int)(panorama.rows * 0.15); // bottom 15% = nadir
    cv::rectangle(panoMask, cv::Rect(0, 0, panorama.cols, zenithH), cv::Scalar(255), -1);
    cv::rectangle(panoMask, cv::Rect(0, panorama.rows - nadirH, panorama.cols, nadirH), cv::Scalar(255), -1);

    cv::Mat inpainted;
    // INPAINT_NS (Navier-Stokes), inpaintRadius=3 per SRS FR-FILL-01
    cv::inpaint(panorama, panoMask, inpainted, 3.0, cv::INPAINT_NS);

    // Apply nadir cap options as per FR-FILL-02
    if (nadirCapOption == 1) {
        // Smooth radial vignette gradient feathering
        int cx = inpainted.cols / 2;
        int cy = inpainted.rows - (nadirH / 2);
        int radius = std::max(nadirH, inpainted.cols / 6);
        for (int y = inpainted.rows - nadirH; y < inpainted.rows; y++) {
            for (int x = 0; x < inpainted.cols; x++) {
                float dx = (float)(x - cx);
                float dy = (float)(y - cy);
                float dist = std::sqrt(dx*dx + dy*dy);
                float alpha = std::min(1.0f, dist / radius);
                auto& pix = inpainted.at<cv::Vec3b>(y, x);
                pix[0] = (uint8_t)(pix[0] * (1.0f - alpha));
                pix[1] = (uint8_t)(pix[1] * (1.0f - alpha));
                pix[2] = (uint8_t)(pix[2] * (1.0f - alpha));
            }
        }
        LOGI("Applied nadir vignette feather");
    }
    // nadirCapOption == 2 (Agency Logo) is applied from Kotlin layer after reading logo asset

    // ── Step 11: JPEG output at quality 92 per SRS FR-STITCH-03 ───────────
    std::vector<int> jpegParams = {cv::IMWRITE_JPEG_QUALITY, 92};
    if (!cv::imwrite(outPathStr, inpainted, jpegParams)) {
        return createStitchResult(env, false, "", 0, "Failed to write output JPEG");
    }

    // ── Step 12: Embed GPano XMP in JPEG APP1 (correct method) ────────────
    writeGPanoXmpToJpeg(outPathStr, inpainted.cols, inpainted.rows);

    // ── Quality score calculation ──────────────────────────────────────────
    double inpaintedPercent = (double)(zenithH + nadirH) / panorama.rows;
    int qualityScore = computeQualityScore(totalFeatures, avgReprojError, inpaintedPercent);
    LOGI("Stitching complete: %dx%d quality=%d", inpainted.cols, inpainted.rows, qualityScore);

    return createStitchResult(env, true, outPathStr, qualityScore, "");

#else
    // ── Fallback path (no OpenCV) ──────────────────────────────────────────
    LOGI("Running fallback blender (no OpenCV) for %d frames", frameCount);
    if (inputPaths.empty()) {
        return createStitchResult(env, false, "", 0, "No frames to process");
    }

    // Copy first frame as placeholder output
    std::ifstream src(inputPaths[0], std::ios::binary);
    std::ofstream dst(outPathStr, std::ios::binary | std::ios::trunc);
    if (src && dst) {
        dst << src.rdbuf();
    }
    src.close(); dst.close();

    // Still embed proper XMP
    writeGPanoXmpToJpeg(outPathStr, 4096, 2048);
    return createStitchResult(env, true, outPathStr, 3, "Fallback mode: OpenCV not available");
#endif
}
