/*
 * Copyright (C) 2025 Kevin Buzeau
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <android/log.h>
#include <android/bitmap.h>
#include <jni.h>
#include <opencv2/imgproc/imgproc.hpp>
#include <string>
#include <map>

#include "jni/jni.hpp"
#include "detector/detector.hpp"

using namespace smartautoclicker;

namespace {

    /**
     * Safely converts standard UTF-8 std::string to a JNI jstring via UTF-16,
     * avoiding JNI NewStringUTF crashes on 4-byte supplementary UTF-8 characters or malformed bytes.
     */
    jstring utf8ToJString(JNIEnv* env, const std::string& utf8) {
        std::vector<jchar> utf16;
        utf16.reserve(utf8.size());

        const auto* data = reinterpret_cast<const unsigned char*>(utf8.data());
        const size_t len = utf8.size();
        size_t i = 0;

        while (i < len) {
            uint32_t cp = 0;
            unsigned char c = data[i];
            if (c < 0x80) {
                cp = c;
                i += 1;
            } else if ((c & 0xE0) == 0xC0 && i + 1 < len && (data[i + 1] & 0xC0) == 0x80) {
                cp = ((c & 0x1F) << 6) | (data[i + 1] & 0x3F);
                i += 2;
            } else if ((c & 0xF0) == 0xE0 && i + 2 < len &&
                       (data[i + 1] & 0xC0) == 0x80 && (data[i + 2] & 0xC0) == 0x80) {
                cp = ((c & 0x0F) << 12) | ((data[i + 1] & 0x3F) << 6) | (data[i + 2] & 0x3F);
                i += 3;
            } else if ((c & 0xF8) == 0xF0 && i + 3 < len &&
                       (data[i + 1] & 0xC0) == 0x80 && (data[i + 2] & 0xC0) == 0x80 &&
                       (data[i + 3] & 0xC0) == 0x80) {
                cp = ((c & 0x07) << 18) | ((data[i + 1] & 0x3F) << 12) |
                     ((data[i + 2] & 0x3F) << 6) | (data[i + 3] & 0x3F);
                i += 4;
            } else {
                i += 1;
                continue;
            }

            if (cp <= 0xFFFF) {
                utf16.push_back(static_cast<jchar>(cp));
            } else if (cp <= 0x10FFFF) {
                cp -= 0x10000;
                utf16.push_back(static_cast<jchar>(0xD800 + (cp >> 10)));
                utf16.push_back(static_cast<jchar>(0xDC00 + (cp & 0x3FF)));
            }
        }

        return env->NewString(utf16.data(), static_cast<jsize>(utf16.size()));
    }

} // namespace

extern "C" {

    JNIEXPORT jlong JNICALL Java_com_buzbuz_smartautoclicker_core_detection_NativeDetector_newDetector(
            JNIEnv *env,
            jobject self
    ) {
        return reinterpret_cast<jlong>(new Detector());
    }

    JNIEXPORT jboolean JNICALL Java_com_buzbuz_smartautoclicker_core_detection_NativeDetector_loadDetectionModels(
            JNIEnv* env,
            jobject self,
            jstring detectionModelPath,
            jobjectArray recognitionModelIds,
            jobjectArray recognitionModelPaths
    ) {
        const char* nativeDetectionPath = env->GetStringUTFChars(detectionModelPath, nullptr);

        // Convert parallel arrays back to std::map<std::string, std::string>
        std::map<std::string, std::string> recognitionModels;
        jsize length = env->GetArrayLength(recognitionModelIds);

        for (jsize i = 0; i < length; i++) {
            auto key = (jstring) env->GetObjectArrayElement(recognitionModelIds, i);
            auto value = (jstring) env->GetObjectArrayElement(recognitionModelPaths, i);

            const char* nativeKey = env->GetStringUTFChars(key, nullptr);
            const char* nativeValue = env->GetStringUTFChars(value, nullptr);

            recognitionModels[nativeKey] = std::string(nativeValue);

            env->ReleaseStringUTFChars(key, nativeKey);
            env->ReleaseStringUTFChars(value, nativeValue);
            env->DeleteLocalRef(key);
            env->DeleteLocalRef(value);
        }

        auto detector = getDetectorFromJavaRef(env, self);
        bool result = false;
        if (detector) {
            result = detector->loadModels(nativeDetectionPath, recognitionModels);
        }

        env->ReleaseStringUTFChars(detectionModelPath, nativeDetectionPath);

        return result ? JNI_TRUE : JNI_FALSE;
    }

    JNIEXPORT void JNICALL Java_com_buzbuz_smartautoclicker_core_detection_NativeDetector_setScreenImage(
            JNIEnv *env,
            jobject self,
            jobject screenBitmap,
            jstring metricsTag
    ) {
        const char* nativeMetricsTag = env->GetStringUTFChars(metricsTag, nullptr);
        if (nativeMetricsTag == nullptr) return;

        auto detector = getDetectorFromJavaRef(env, self);
        if (!detector) return;

        std::unique_ptr<cv::Mat> screenMat = loadMatFromRGBA8888Bitmap(env, screenBitmap);
        if (!screenMat) return;

        detector->setScreenImage(std::move(screenMat), nativeMetricsTag);
        env->ReleaseStringUTFChars(metricsTag, nativeMetricsTag);
    }

    JNIEXPORT jdoubleArray JNICALL Java_com_buzbuz_smartautoclicker_core_detection_NativeDetector_detectImageNative(
            JNIEnv *env,
            jobject self,
            jobject conditionBitmap,
            jint conditionWidth,
            jint conditionHeight,
            jint x,
            jint y,
            jint width,
            jint height,
            jint threshold
    ) {
        auto detector = getDetectorFromJavaRef(env, self);
        if (!detector) return nullptr;

        std::unique_ptr<cv::Mat> conditionMat = loadMatFromRGBA8888Bitmap(env, conditionBitmap);
        if (!conditionMat) return nullptr;

        jdoubleArray result = nullptr;
        try {
            result = toJniResult(env, detector->detectImage(
                    std::move(conditionMat),
                    conditionWidth,
                    conditionHeight,
                    cv::Rect(x, y, width, height),
                    threshold));
        } catch (...) {
            releaseBitmapLock(env, conditionBitmap);
            throwRuntimeException(env, "Invalid detection arguments for image detection");
        }

        releaseBitmapLock(env, conditionBitmap);
        return result;
    }

    JNIEXPORT jdoubleArray JNICALL Java_com_buzbuz_smartautoclicker_core_detection_NativeDetector_detectColorNative(
            JNIEnv *env,
            jobject self,
            jint conditionColor,
            jint x,
            jint y,
            jint width,
            jint height,
            jint threshold
    ) {
        auto detector = getDetectorFromJavaRef(env, self);
        if (!detector) return nullptr;

        try {
            return toJniResult(env, detector->detectColor(
                    conditionColor,
                    cv::Rect(x, y, width, height),
                    threshold));
        } catch (...) {
            throwRuntimeException(env, "Invalid detection arguments for color detection");
            return nullptr;
        }
    }

    JNIEXPORT jdoubleArray JNICALL Java_com_buzbuz_smartautoclicker_core_detection_NativeDetector_detectTextNative(
            JNIEnv *env,
            jobject self,
            jstring conditionText,
            jstring recognitionModelId,
            jint x,
            jint y,
            jint width,
            jint height,
            jint threshold
    ) {
        auto detector = getDetectorFromJavaRef(env, self);
        if (!detector) return nullptr;

        const char* nativeConditionText = env->GetStringUTFChars(conditionText, nullptr);
        const char* nativeRecognitionModelId = env->GetStringUTFChars(recognitionModelId, nullptr);
        if (nativeConditionText == nullptr || nativeRecognitionModelId == nullptr) return nullptr;

        jdoubleArray result = nullptr;
        try {
            result = toJniResult(env, detector->detectText(
                    nativeConditionText,
                    nativeRecognitionModelId,
                    cv::Rect(x, y, width, height),
                    threshold));
        } catch (...) {
            throwRuntimeException(env, "Invalid detection arguments for text detection");
        }

        env->ReleaseStringUTFChars(conditionText, nativeConditionText);
        env->ReleaseStringUTFChars(recognitionModelId, nativeRecognitionModelId);
        return result;
    }

    JNIEXPORT jdoubleArray JNICALL Java_com_buzbuz_smartautoclicker_core_detection_NativeDetector_detectNumberNative(
            JNIEnv *env,
            jobject self,
            jint x,
            jint y,
            jint width,
            jint height,
            jint threshold,
            jint numberFormat
    ) {
        auto detector = getDetectorFromJavaRef(env, self);
        if (!detector) return nullptr;

        try {
            return toJniResult(env, detector->detectNumber(
                    cv::Rect(x, y, width, height),
                    threshold,
                    static_cast<NumberFormat>(numberFormat)));
        } catch (...) {
            throwRuntimeException(env, "Invalid detection arguments for number detection");
        }

        return nullptr;
    }

    JNIEXPORT jstring JNICALL Java_com_buzbuz_smartautoclicker_core_detection_NativeDetector_readTextNative(
            JNIEnv *env,
            jobject self,
            jstring recognitionModelId,
            jint x,
            jint y,
            jint width,
            jint height,
            jdoubleArray outMetrics
    ) {
        auto detector = getDetectorFromJavaRef(env, self);
        if (!detector) return nullptr;

        const char* nativeRecognitionModelId = env->GetStringUTFChars(recognitionModelId, nullptr);
        if (nativeRecognitionModelId == nullptr) return nullptr;

        jstring resultString = nullptr;
        try {
            TextReadResult readResult = detector->readText(
                    nativeRecognitionModelId,
                    cv::Rect(x, y, width, height));

            if (outMetrics != nullptr && env->GetArrayLength(outMetrics) >= 5) {
                jdouble metrics[5] = {
                        static_cast<double>(readResult.confidence),
                        static_cast<double>(readResult.boundingBox.x),
                        static_cast<double>(readResult.boundingBox.y),
                        static_cast<double>(readResult.boundingBox.x + readResult.boundingBox.width),
                        static_cast<double>(readResult.boundingBox.y + readResult.boundingBox.height)
                };
                env->SetDoubleArrayRegion(outMetrics, 0, 5, metrics);
            }

            resultString = utf8ToJString(env, readResult.text);
        } catch (...) {
            throwRuntimeException(env, "Invalid detection arguments for readText");
        }

        env->ReleaseStringUTFChars(recognitionModelId, nativeRecognitionModelId);
        return resultString;
    }

    JNIEXPORT void JNICALL Java_com_buzbuz_smartautoclicker_core_detection_NativeDetector_releaseScreenImage(
            JNIEnv *env,
            jobject self,
            jobject screenBitmap
    ) {
        releaseBitmapLock(env, screenBitmap);
    }

    JNIEXPORT void JNICALL Java_com_buzbuz_smartautoclicker_core_detection_NativeDetector_deleteDetector(
            JNIEnv *env,
            jobject self
    ) {
        auto detector = getDetectorFromJavaRef(env, self);
        if (!detector) return;

        delete detector;
    }
}
