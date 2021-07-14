package com.volmit.iris.manager.report;

import lombok.Builder;

@Builder
public class Report {
    @Builder.Default
    private final ReportType type = ReportType.NOTICE;
    @Builder.Default
    private final String title = "Problem...";
    @Builder.Default
    private final String message = "No Message";
    @Builder.Default
    private final String suggestion = "No Suggestion";

    public String toString() {
        return type.toString() + ": " + title + ": " + message + ": Suggestion: " + suggestion;
    }
}
