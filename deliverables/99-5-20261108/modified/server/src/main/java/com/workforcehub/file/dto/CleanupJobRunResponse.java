package com.workforcehub.file.dto;

import lombok.Data;

@Data
public class CleanupJobRunResponse {

    private String jobRunId;
    private String jobRunNo;
    private String jobType;
    private String jobTypeName;
    private Boolean dryRun;
    private String jobStatus;
    private String jobStatusName;
    private Long expiredUploadTickets;
    private Long expiredDownloadTickets;
    private Long purgedAttachmentRefs;
    private Long deletedPhysicalObjects;
    private Long failedPhysicalObjects;
    private Long releasedBytes;
    private String errorMessage;
}
