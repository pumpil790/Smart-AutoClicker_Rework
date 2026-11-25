/*
 * Copyright (C) 2026 Kevin Buzeau
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

#include <opencv2/imgproc/imgproc.hpp>
#include <opencv2/imgproc/imgproc_c.h>
#include <cctype>
#include <cmath>
#include <algorithm>
#include <limits>
#include <numeric>

#include "text_matcher.hpp"
#include "text_matching_core.hpp"
#include "../../../logs/log.h"
#include "../../../utils/roi.h"

using namespace smartautoclicker;

namespace {

    std::string collapseAndTrimSpaces(const std::string& input) {
        std::string out;
        out.reserve(input.size());
        bool inSpace = true;
        for (unsigned char ch : input) {
            if (ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r') {
                if (!inSpace) {
                    out.push_back(' ');
                    inSpace = true;
                }
            } else {
                out.push_back(static_cast<char>(ch));
                inSpace = false;
            }
        }
        while (!out.empty() && out.back() == ' ') {
            out.pop_back();
        }
        return out;
    }

    cv::Mat getRgbScreenCrop(const ScreenImage& screenImage, const cv::Rect& detectionArea) {
        cv::Mat screenCrop = screenImage.cropColor(detectionArea);
        if (screenCrop.empty()) return {};
        cv::Mat rgbScreenCrop;
        cv::cvtColor(screenCrop, rgbScreenCrop, cv::COLOR_RGBA2RGB);
        return rgbScreenCrop;
    }

} // namespace

bool TextMatcher::init(const std::string& detectionModelPath, const std::map<std::string, std::string>& recognitionModels) {
    if (!recognitionModels.empty()) {
        defaultRecognitionModelId = recognitionModels.begin()->first;
    }
    return textLocator->init(detectionModelPath) && textRecognizer->init(recognitionModels);
}

bool TextMatcher::isInitialized() const {
    return textLocator->isInitialized && textRecognizer->isInitialized;
}

void TextMatcher::clearResults() {
    currentMatchingResult.reset();
}

TextMatchingResult* TextMatcher::matchText(
        const ScreenImage& screenImage,
        const std::string& conditionText,
        const std::string& recognitionModelId,
        const cv::Rect& detectionArea,
        int threshold)
{
    clearResults();

    if (!isInitialized() || !isRoiValidForMatching(screenImage.getRoi(), detectionArea)) {
        LOGE("TextMatcher", "Can't match text, invalid state or RoI (x=%d, y=%d, w=%d, h=%d)",
             detectionArea.x, detectionArea.y, detectionArea.width, detectionArea.height);
        return &currentMatchingResult;
    }

    const bool foldCyrillicHomoglyphs = (recognitionModelId == "CYRILLIC");
    const auto normalizedTarget = decodeAndNormalizeUtf8(conditionText, true, foldCyrillicHomoglyphs);
    if (normalizedTarget.empty()) {
        return &currentMatchingResult;
    }
    const float minSimilarity = getAdaptiveMinSimilarity(normalizedTarget.size());

    cv::Mat rgbScreenCrop = getRgbScreenCrop(screenImage, detectionArea);
    if (rgbScreenCrop.empty()) {
        LOGE("TextMatcher", "Can't get rgb screen crop");
        return &currentMatchingResult;
    }

    // Pass 1: Original unmodified RGB crop (zero regression for black text on white background)
    auto pass1Results = recognizeTextOnRgb(rgbScreenCrop, recognitionModelId);
    evaluateTextMatches(pass1Results, normalizedTarget, minSimilarity, foldCyrillicHomoglyphs, detectionArea, threshold);

    // Pass 2: Fallback with background inversion + CLAHE only if Pass 1 did not match
    if (!currentMatchingResult.isDetected()) {
        cv::Mat enhancedRgb = preprocessColorText(rgbScreenCrop);
        if (!enhancedRgb.empty()) {
            auto pass2Results = recognizeTextOnRgb(enhancedRgb, recognitionModelId);
            evaluateTextMatches(pass2Results, normalizedTarget, minSimilarity, foldCyrillicHomoglyphs, detectionArea, threshold);
        }
    }

    return &currentMatchingResult;
}

TextMatchingResult* TextMatcher::matchNumber(
        const ScreenImage& screenImage,
        const cv::Rect& detectionArea,
        int threshold,
        NumberFormat numberFormat
) {
    clearResults();

    if (!isInitialized() || !isRoiValidForMatching(screenImage.getRoi(), detectionArea)) {
        LOGE("TextMatcher", "Can't match text, invalid state or RoI (x=%d, y=%d, w=%d, h=%d)",
             detectionArea.x, detectionArea.y, detectionArea.width, detectionArea.height);
        return &currentMatchingResult;
    }

    if (defaultRecognitionModelId.empty()) {
        LOGE("TextMatcher", "Can't match number, no recognition model available");
        return &currentMatchingResult;
    }

    cv::Mat rgbScreenCrop = getRgbScreenCrop(screenImage, detectionArea);
    if (rgbScreenCrop.empty()) {
        LOGE("TextMatcher", "Can't get rgb screen crop");
        return &currentMatchingResult;
    }

    // Pass 1: Original
    auto pass1Results = recognizeTextOnRgb(rgbScreenCrop, defaultRecognitionModelId);
    evaluateNumberMatches(pass1Results, detectionArea, threshold, numberFormat);

    // Pass 2: Fallback only if no number detected in Pass 1
    if (!currentMatchingResult.isDetected()) {
        cv::Mat enhancedRgb = preprocessColorText(rgbScreenCrop);
        if (!enhancedRgb.empty()) {
            auto pass2Results = recognizeTextOnRgb(enhancedRgb, defaultRecognitionModelId);
            evaluateNumberMatches(pass2Results, detectionArea, threshold, numberFormat);
        }
    }

    return &currentMatchingResult;
}

TextReadResult TextMatcher::readText(
        const ScreenImage& screenImage,
        const std::string& recognitionModelId,
        const cv::Rect& detectionArea
) {
    TextReadResult emptyResult;

    if (!isInitialized() || !isRoiValidForMatching(screenImage.getRoi(), detectionArea)) {
        LOGE("TextMatcher", "Can't read text, invalid state or RoI (x=%d, y=%d, w=%d, h=%d)",
             detectionArea.x, detectionArea.y, detectionArea.width, detectionArea.height);
        return emptyResult;
    }

    cv::Mat rgbScreenCrop = getRgbScreenCrop(screenImage, detectionArea);
    if (rgbScreenCrop.empty()) {
        LOGE("TextMatcher", "Can't get rgb screen crop");
        return emptyResult;
    }

    // Pass 1: Original RGB crop
    auto pass1Blocks = recognizeTextOnRgb(rgbScreenCrop, recognitionModelId);
    TextReadResult bestResult = joinRecognitionResults(pass1Blocks);

    // Pass 2: Fallback only if Pass 1 produced no text or low confidence
    if (bestResult.text.empty() || bestResult.confidence < READ_TEXT_FALLBACK_CONFIDENCE) {
        cv::Mat enhancedRgb = preprocessColorText(rgbScreenCrop);
        if (!enhancedRgb.empty()) {
            auto pass2Blocks = recognizeTextOnRgb(enhancedRgb, recognitionModelId);
            TextReadResult pass2Result = joinRecognitionResults(pass2Blocks);
            if (!pass2Result.text.empty() &&
                (bestResult.text.empty() || pass2Result.confidence > bestResult.confidence)) {
                bestResult = std::move(pass2Result);
            }
        }
    }

    if (!bestResult.text.empty()) {
        bestResult.boundingBox.x += detectionArea.x;
        bestResult.boundingBox.y += detectionArea.y;
    }

    LOGD("TextMatcher", "readText=\"%s\" (conf=%.3f, box=%d,%d,%d,%d)",
         bestResult.text.c_str(), bestResult.confidence,
         bestResult.boundingBox.x, bestResult.boundingBox.y,
         bestResult.boundingBox.width, bestResult.boundingBox.height);

    return bestResult;
}

bool TextMatcher::isRoiValidForMatching(const cv::Rect& screenRoi, const cv::Rect& roi) {
    if (roi.width <= 0 || roi.height <= 0 || !isRoiContainsOrEquals(screenRoi, roi)) {
        LOGD("TextMatcher", "Can't detect text, detection area (x=%d, y=%d, w=%d, h=%d) is not contained in screen (w=%d, h=%d)",
             roi.x, roi.y, roi.width, roi.height,
             screenRoi.width, screenRoi.height);
        return false;
    }

    return true;
}

cv::Mat TextMatcher::preprocessColorText(const cv::Mat& rgbCrop) {
    if (rgbCrop.empty()) return {};

    cv::Mat gray;
    cv::cvtColor(rgbCrop, gray, cv::COLOR_RGB2GRAY);

    const int minDim = std::min(gray.rows, gray.cols);
    const int border = std::max(1, std::min(4, minDim / 4));

    double borderSum = 0.0;
    int borderCount = 0;

    // Top and bottom strips
    for (int y = 0; y < border; ++y) {
        const uchar* topRow = gray.ptr<uchar>(y);
        const uchar* botRow = gray.ptr<uchar>(gray.rows - 1 - y);
        for (int x = 0; x < gray.cols; ++x) {
            borderSum += topRow[x];
            borderCount++;
            if (gray.rows - 1 - y != y) {
                borderSum += botRow[x];
                borderCount++;
            }
        }
    }
    // Left and right strips (excluding corners already counted)
    for (int y = border; y < gray.rows - border; ++y) {
        const uchar* row = gray.ptr<uchar>(y);
        for (int x = 0; x < border; ++x) {
            borderSum += row[x];
            borderCount++;
            if (gray.cols - 1 - x != x) {
                borderSum += row[gray.cols - 1 - x];
                borderCount++;
            }
        }
    }

    double meanBorder = borderCount > 0 ? borderSum / static_cast<double>(borderCount) : cv::mean(gray)[0];
    double meanCenter = meanBorder;
    if (gray.cols > border * 2 && gray.rows > border * 2) {
        cv::Mat center = gray(cv::Rect(border, border, gray.cols - border * 2, gray.rows - border * 2));
        meanCenter = cv::mean(center)[0];
    }

    double minVal = 0.0;
    double maxVal = 0.0;
    cv::minMaxLoc(gray, &minVal, &maxVal);

    // Invert when background (border) is darker than foreground text or dark/colored with bright peaks
    if (meanBorder < meanCenter || (meanBorder < 170.0 && maxVal > meanBorder + 30.0)) {
        cv::bitwise_not(gray, gray);
    }

    cv::Mat enhanced;
    cv::Ptr<cv::CLAHE> clahe = cv::createCLAHE(2.0, cv::Size(8, 8));
    clahe->apply(gray, enhanced);

    cv::Mat enhancedRgb;
    cv::cvtColor(enhanced, enhancedRgb, cv::COLOR_GRAY2RGB);
    return enhancedRgb;
}

std::vector<TextRecognizerResult> TextMatcher::recognizeTextOnRgb(
        const cv::Mat& rgbCrop,
        const std::string& recognitionModelId
) {
    if (rgbCrop.empty()) return {};
    auto detectorResults = textLocator->detectText(rgbCrop);
    return textRecognizer->recognizeText(recognitionModelId, detectorResults);
}

std::vector<TextRecognizerResult> TextMatcher::recognizeText(
        const ScreenImage& screenImage,
        const cv::Rect& detectionArea,
        const std::string& recognitionModelId
) {
    cv::Mat rgbScreenCrop = getRgbScreenCrop(screenImage, detectionArea);
    if (rgbScreenCrop.empty()) {
        LOGE("TextMatcher", "Can't get rgb screen crop");
        return {};
    }
    return recognizeTextOnRgb(rgbScreenCrop, recognitionModelId);
}

TextReadResult TextMatcher::joinRecognitionResults(const std::vector<TextRecognizerResult>& results) {
    TextReadResult output;
    if (results.empty()) return output;

    // Filter out blocks with empty/whitespace-only text
    std::vector<const TextRecognizerResult*> validBlocks;
    validBlocks.reserve(results.size());
    for (const auto& block : results) {
        if (!collapseAndTrimSpaces(block.text).empty()) {
            validBlocks.push_back(&block);
        }
    }
    if (validBlocks.empty()) return output;

    // Sort initially by vertical center
    std::sort(validBlocks.begin(), validBlocks.end(), [](const TextRecognizerResult* a, const TextRecognizerResult* b) {
        const int cyA = a->boundingBox.y + a->boundingBox.height / 2;
        const int cyB = b->boundingBox.y + b->boundingBox.height / 2;
        if (cyA != cyB) return cyA < cyB;
        return a->boundingBox.x < b->boundingBox.x;
    });

    // Group into lines based on Y overlap
    struct LineGroup {
        int minY = 0;
        int maxY = 0;
        std::vector<const TextRecognizerResult*> blocks;
    };

    std::vector<LineGroup> lines;
    for (const auto* block : validBlocks) {
        const int bTop = block->boundingBox.y;
        const int bBottom = block->boundingBox.y + block->boundingBox.height;
        const int bCenterY = (bTop + bBottom) / 2;

        LineGroup* matchedLine = nullptr;
        for (auto& line : lines) {
            const int overlap = std::min(line.maxY, bBottom) - std::max(line.minY, bTop);
            const int minHeight = std::max(1, std::min(line.maxY - line.minY, block->boundingBox.height));
            const bool centerInside = (bCenterY >= line.minY && bCenterY <= line.maxY);
            if (overlap >= static_cast<int>(0.4f * static_cast<float>(minHeight)) || centerInside) {
                matchedLine = &line;
                break;
            }
        }

        if (matchedLine != nullptr) {
            matchedLine->blocks.push_back(block);
            matchedLine->minY = std::min(matchedLine->minY, bTop);
            matchedLine->maxY = std::max(matchedLine->maxY, bBottom);
        } else {
            lines.push_back(LineGroup{bTop, bBottom, {block}});
        }
    }

    // Sort lines top-to-bottom
    std::sort(lines.begin(), lines.end(), [](const LineGroup& a, const LineGroup& b) {
        return a.minY < b.minY;
    });

    std::string joined;
    float confidenceSum = 0.0f;
    int blockCount = 0;
    bool hasBox = false;
    cv::Rect unionBox;

    for (auto& line : lines) {
        // Sort blocks within each line left-to-right
        std::sort(line.blocks.begin(), line.blocks.end(), [](const TextRecognizerResult* a, const TextRecognizerResult* b) {
            return a->boundingBox.x < b->boundingBox.x;
        });

        for (const auto* block : line.blocks) {
            std::string cleaned = collapseAndTrimSpaces(block->text);
            if (cleaned.empty()) continue;

            if (!joined.empty()) joined.push_back(' ');
            joined += cleaned;
            confidenceSum += block->confidence;
            blockCount++;

            if (!hasBox) {
                unionBox = block->boundingBox;
                hasBox = true;
            } else {
                unionBox |= block->boundingBox;
            }
        }
    }

    output.text = collapseAndTrimSpaces(joined);
    output.confidence = blockCount > 0 ? (confidenceSum / static_cast<float>(blockCount)) : 0.0f;
    output.boundingBox = unionBox;
    return output;
}

void TextMatcher::evaluateTextMatches(
        const std::vector<TextRecognizerResult>& recognizerResults,
        const std::vector<uint32_t>& normalizedTarget,
        float minSimilarity,
        bool foldCyrillicHomoglyphs,
        const cv::Rect& detectionArea,
        int threshold)
{
    for (const auto& recognizerResult : recognizerResults) {
        auto normalizedRecognized = decodeAndNormalizeUtf8(recognizerResult.text, true, foldCyrillicHomoglyphs);
        float score = bestSubstringSimilarity(normalizedRecognized, normalizedTarget, minSimilarity) * 100.f;
        LOGD("TextMatcher", "Score=%f; recognized=%s", score, recognizerResult.text.c_str());

        if (score < currentMatchingResult.getResultConfidence()) continue;

        currentMatchingResult.updateResults(detectionArea, recognizerResult.boundingBox, score);
        if (static_cast<int>(score) >= threshold) {
            currentMatchingResult.markResultAsDetected();
            return;
        }
    }

    // Multiline fallback: if no individual block matched and there are >= 2 blocks, evaluate joined reading-order text
    if (!currentMatchingResult.isDetected() && recognizerResults.size() >= 2) {
        TextReadResult joined = joinRecognitionResults(recognizerResults);
        if (!joined.text.empty()) {
            auto normalizedJoined = decodeAndNormalizeUtf8(joined.text, true, foldCyrillicHomoglyphs);
            float score = bestSubstringSimilarity(normalizedJoined, normalizedTarget, minSimilarity) * 100.f;
            LOGD("TextMatcher", "Joined Score=%f; joined=%s", score, joined.text.c_str());

            if (score >= currentMatchingResult.getResultConfidence()) {
                currentMatchingResult.updateResults(detectionArea, joined.boundingBox, score);
                if (static_cast<int>(score) >= threshold) {
                    currentMatchingResult.markResultAsDetected();
                }
            }
        }
    }
}

void TextMatcher::evaluateNumberMatches(
        const std::vector<TextRecognizerResult>& recognizerResults,
        const cv::Rect& detectionArea,
        int threshold,
        NumberFormat numberFormat)
{
    for (const auto& recognizerResult : recognizerResults) {
        if (!isNumber(recognizerResult.text)) continue;

        float score = recognizerResult.confidence * 100.f;
        auto recognizedNumber = stringToDouble(recognizerResult.text, numberFormat);
        LOGD("TextMatcher", "Score=%f; recognized=%f", score, recognizedNumber);

        // Score is below the best confidence, skip
        if (score < currentMatchingResult.getResultConfidence()) continue;

        currentMatchingResult.updateResults(
                detectionArea,
                recognizerResult.boundingBox,
                score,
                recognizedNumber);

        if (static_cast<int>(score) >= threshold) {
            currentMatchingResult.markResultAsDetected();
        }
    }
}

float TextMatcher::bestSubstringSimilarity(
        const std::vector<uint32_t>& recognized,
        const std::vector<uint32_t>& target,
        float minSimilarity)
{
    return TextMatchingCore::bestSubstringSimilarity(
            recognized,
            target,
            minSimilarity,
            comparisonPrevPrevRow,
            comparisonPrevRow,
            comparisonCurrRow);
}

float TextMatcher::similarity(
        const std::vector<uint32_t>& recognized,
        const std::vector<uint32_t>& target,
        float minSimilarity)
{
    return TextMatchingCore::similarity(
            recognized,
            target,
            minSimilarity,
            comparisonPrevPrevRow,
            comparisonPrevRow,
            comparisonCurrRow);
}

uint32_t TextMatcher::normalizeCodepoint(uint32_t cp, bool foldCyrillicHomoglyphs) {
    return TextMatchingCore::normalizeCodepoint(cp, foldCyrillicHomoglyphs);
}

std::vector<uint32_t> TextMatcher::decodeAndNormalizeUtf8(
        const std::string& text,
        bool stripTrailingPunctuation,
        bool foldCyrillicHomoglyphs) {
    return TextMatchingCore::decodeAndNormalizeUtf8(text, stripTrailingPunctuation, foldCyrillicHomoglyphs);
}

float TextMatcher::getAdaptiveMinSimilarity(size_t targetCodepoints) {
    return TextMatchingCore::getAdaptiveMinSimilarity(targetCodepoints);
}

bool TextMatcher::isNumber(const std::string& text) {
    return TextMatchingCore::isNumber(text);
}

double TextMatcher::stringToDouble(const std::string& text, NumberFormat format) {
    std::string s = text;
    // Remove spaces
    s.erase(std::remove(s.begin(), s.end(), ' '), s.end());

    char decimalSep;
    char thousandsSep;

    if (format == NumberFormat::DOT_DECIMAL) {
        decimalSep   = '.';
        thousandsSep = ',';
    } else if (format == NumberFormat::COMMA_DECIMAL) {
        decimalSep   = ',';
        thousandsSep = '.';
    } else {
        // AUTO: infer from structure.
        // If both separators are present, the last one is the decimal separator.
        size_t lastDot   = s.rfind('.');
        size_t lastComma = s.rfind(',');

        if (lastDot != std::string::npos && lastComma != std::string::npos) {
            decimalSep   = (lastDot > lastComma) ? '.' : ',';
            thousandsSep = (lastDot > lastComma) ? ',' : '.';
        } else if (lastComma != std::string::npos) {
            // Only a comma: thousands separator if exactly 3 digits follow it, else decimal.
            size_t digitsAfterComma = s.size() - lastComma - 1;
            if (digitsAfterComma == 3 && lastComma > 0) {
                decimalSep   = '.';
                thousandsSep = ',';
            } else {
                decimalSep   = ',';
                thousandsSep = '.';
            }
        } else {
            // Only a dot (or none): treat dot as decimal separator.
            decimalSep   = '.';
            thousandsSep = ',';
        }
    }

    // Strip thousands separators, then normalise decimal separator to '.'.
    std::string sanitized;
    sanitized.reserve(s.size());
    for (char c : s) {
        if (c == thousandsSep) continue;
        sanitized += (c == decimalSep) ? '.' : c;
    }

    try {
        return std::stod(sanitized);
    } catch (...) {
        return std::numeric_limits<double>::lowest();
    }
}
