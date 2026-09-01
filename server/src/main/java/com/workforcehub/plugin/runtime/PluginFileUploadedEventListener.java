package com.workforcehub.plugin.runtime;

import com.workforcehub.common.event.file.FileUploadedEvent;
import com.workforcehub.plugin.sdk.DomainEvent;
import com.workforcehub.plugin.sdk.EventTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 将文件上传领域事件桥接到插件事件分发器。
 */
@Component
public class PluginFileUploadedEventListener {

    private static final Logger log = LoggerFactory.getLogger(PluginFileUploadedEventListener.class);

    private final PluginEventDispatcher eventDispatcher;
    private final PluginDomainEventAdapter eventAdapter = new PluginDomainEventAdapter();

    public PluginFileUploadedEventListener(PluginEventDispatcher eventDispatcher) {
        this.eventDispatcher = eventDispatcher;
    }

    @EventListener
    public void onFileUploaded(FileUploadedEvent event) {
        try {
            String eventId = "file-uploaded-" + event.attachmentRefId();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("attachmentRefId", event.attachmentRefId());
            payload.put("ownerType", event.ownerType());
            payload.put("ownerId", event.ownerId());
            payload.put("displayName", event.displayName());
            payload.put("mimeType", event.mimeType());
            payload.put("fileSize", event.fileSize());
            payload.put("uploaderType", event.uploaderType());
            payload.put("uploaderId", event.uploaderId());
            payload.put("scanStatus", event.scanStatus());

            DomainEvent pluginEvent = eventAdapter.adapt(
                event,
                EventTypes.FILE_UPLOADED,
                eventId,
                eventId,
                payload
            );
            eventDispatcher.dispatch(pluginEvent);
        } catch (Throwable e) {
            if (e instanceof VirtualMachineError error) {
                throw error;
            }
            if (e instanceof ThreadDeath threadDeath) {
                throw threadDeath;
            }
            log.warn(
                "文件上传插件事件分发失败: attachmentRefId={}",
                event.attachmentRefId(),
                e
            );
        }
    }
}
