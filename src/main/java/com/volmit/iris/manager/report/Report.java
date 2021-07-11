package com.volmit.iris.manager.report;

import lombok.Builder;

import java.io.File;

@Builder
public class Report
{
    @Builder.Default
    private ReportType type = ReportType.NOTICE;
    @Builder.Default
    private String title = "Problem...";
    @Builder.Default
    private String message = "No Message";
    @Builder.Default
    private String suggestion = "No Suggestion";

    public String toString()
    {
        return type.toString() + ": " + title + ": " + message + ": Suggestion: " + suggestion;
    }
}
