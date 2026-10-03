package com.aibuilder.visual;

import java.util.List;

public record VisualReviewResult(

        Verdict verdict,

        String summary,

        List<String> issues,

        String fixInstructions

) {

    public enum Verdict {

        PASS,

        NEEDS_FIX
    }

    public boolean needsFix() {

        return verdict == Verdict.NEEDS_FIX;
    }
}