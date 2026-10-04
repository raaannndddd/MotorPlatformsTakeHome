package com.motorplatforms.media;

import com.motorplatforms.common.ApiException;
import com.motorplatforms.common.AppProperties;
import com.motorplatforms.infra.storage.ObjectStorage;
import com.motorplatforms.inspections.CustomerInspectionService;
import com.motorplatforms.inspections.InspectionStatus;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Direct-to-storage uploads: request a URL, upload to storage, then confirm. */
@Service
public class MediaService {

  static final Set<String> ALLOWED_TYPES =
      Set.of("image/jpeg", "image/png", "image/heic", "video/mp4");

  public record UploadTicket(UUID mediaId, String uploadUrl, String method, String contentType) {}

  private final MediaRepository media;
  private final ObjectStorage storage;
  private final CustomerInspectionService inspections;
  private final long maxBytes;

  public MediaService(
      MediaRepository media,
      ObjectStorage storage,
      CustomerInspectionService inspections,
      AppProperties props) {
    this.media = media;
    this.storage = storage;
    this.inspections = inspections;
    this.maxBytes = props.maxMediaBytes();
  }

  @Transactional
  public UploadTicket requestUpload(UUID inspectionId, String contentType, long sizeBytes) {
    if (!ALLOWED_TYPES.contains(contentType)) {
      throw ApiException.invalidField("contentType", "must be one of " + ALLOWED_TYPES);
    }
    if (sizeBytes > maxBytes) {
      throw ApiException.invalidField("sizeBytes", "must be at most " + maxBytes + " bytes");
    }
    requireEditable(inspectionId);
    String key = inspectionId + "-" + UUID.randomUUID();
    Media saved = media.save(new Media(inspectionId, key, contentType, sizeBytes));
    // The URL only accepts the declared size, so the size check above cannot be bypassed.
    return new UploadTicket(
        saved.getId(), storage.uploadUrl(key, contentType, sizeBytes), "PUT", contentType);
  }

  @Transactional
  public MediaView confirm(UUID inspectionId, UUID mediaId) {
    requireEditable(inspectionId);
    Media item =
        media
            .findByIdAndInspectionId(mediaId, inspectionId)
            .orElseThrow(() -> ApiException.notFound("Media"));
    long stored =
        storage
            .size(item.getObjectKey())
            .orElseThrow(
                () ->
                    ApiException.conflict("UPLOAD_MISSING", "The file has not been uploaded yet."));
    item.confirm(stored);
    return view(item);
  }

  @Transactional(readOnly = true)
  public List<MediaView> confirmedFor(UUID inspectionId) {
    return media.findByInspectionIdAndConfirmedAtIsNotNullOrderByCreatedAt(inspectionId).stream()
        .map(this::view)
        .toList();
  }

  private void requireEditable(UUID inspectionId) {
    if (inspections.get(inspectionId).getStatus() != InspectionStatus.OPENED) {
      throw ApiException.conflict("NOT_EDITABLE", "This inspection has already been submitted.");
    }
  }

  private MediaView view(Media m) {
    return new MediaView(
        m.getId(),
        m.getContentType(),
        m.getSizeBytes(),
        storage.downloadUrl(m.getObjectKey(), m.getContentType()));
  }
}
