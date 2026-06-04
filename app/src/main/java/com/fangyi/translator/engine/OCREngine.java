package com.fangyi.translator.engine;

import android.graphics.Bitmap;
import android.graphics.Rect;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * ML Kit OCR wrapper. Offline, free, supports Latin + Chinese character sets.
 */
public class OCREngine {

    private final TextRecognizer recognizer;
    private final Executor executor = Executors.newSingleThreadExecutor();

    public OCREngine() {
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    }

    /**
     * Recognizes all text blocks in a bitmap with their bounding boxes.
     */
    public Task<List<TextBlock>> recognize(Bitmap bitmap) {
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        return recognizer.process(image)
                .continueWith(executor, task -> {
                    if (!task.isSuccessful() || task.getResult() == null) {
                        return new ArrayList<>();
                    }
                    Text result = task.getResult();
                    List<TextBlock> blocks = new ArrayList<>();
                    for (Text.TextBlock block : result.getTextBlocks()) {
                        for (Text.Line line : block.getLines()) {
                            String text = line.getText();
                            Rect bounds = line.getBoundingBox();
                            if (bounds != null && containsEnglish(text)) {
                                blocks.add(new TextBlock(text, bounds));
                            }
                        }
                    }
                    return blocks;
                });
    }

    public void close() {
        try {
            recognizer.close();
        } catch (Exception ignored) {}
    }

    private boolean containsEnglish(String text) {
        if (text == null || text.isEmpty()) return false;
        for (char c : text.toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                return true;
            }
        }
        return false;
    }

    public static class TextBlock {
        public final String text;
        public final Rect bounds;

        public TextBlock(String text, Rect bounds) {
            this.text = text;
            this.bounds = bounds;
        }
    }
}
