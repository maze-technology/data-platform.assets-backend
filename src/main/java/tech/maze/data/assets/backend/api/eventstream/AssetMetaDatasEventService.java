package tech.maze.data.assets.backend.api.eventstream;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.maze.commons.mappers.ProtobufValueMapper;
import tech.maze.data.assets.backend.api.mappers.PrimaryClassDtoMapper;
import tech.maze.data.assets.backend.domain.models.PrimaryClass;
import tech.maze.data.assets.backend.infrastructure.persistence.entities.AssetDataProviderMetaDataEntity;
import tech.maze.data.assets.backend.infrastructure.persistence.entities.AssetEntity;
import tech.maze.data.assets.backend.infrastructure.persistence.repositories.AssetDataProvidersMetadatasJpaRepository;
import tech.maze.data.assets.backend.infrastructure.persistence.repositories.AssetJpaRepository;
import tech.maze.dtos.assets.models.AssetMetaDatas;

/**
 * Handles single-asset metadata events and persists/upserts related records.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class AssetMetaDatasEventService {
  AssetJpaRepository assetJpaRepository;
  AssetDataProvidersMetadatasJpaRepository assetDataProvidersMetadatasJpaRepository;
  ProtobufValueMapper protobufValueMapper;
  PrimaryClassDtoMapper primaryClassDtoMapper;

  /**
   * Processes one metadata payload (one asset per event) and upserts persistence records.
   */
  @Transactional
  public void process(AssetMetaDatas payload) {
    final UUID dataProviderId = protobufValueMapper.toUuid(payload.getDataProviderId())
        .orElseThrow(() -> new IllegalArgumentException("dataProviderId must be a valid UUID"));
    final String providerAssetId = payload.getAsset().getId();
    if (providerAssetId == null || providerAssetId.isBlank()) {
      throw new IllegalArgumentException("providerAssetId must not be blank");
    }

    final PrimaryClass primaryClass = primaryClassDtoMapper.toDomain(payload.getPrimaryClass());

    final AssetDataProviderMetaDataEntity metaData = assetDataProvidersMetadatasJpaRepository
        .findFirstByDataProviderIdAndProviderAssetId(dataProviderId, providerAssetId)
        .orElseGet(() -> createMetaData(dataProviderId, providerAssetId, payload, primaryClass));

    updateMetaDataIfNeeded(metaData, payload, primaryClass);
  }

  private AssetDataProviderMetaDataEntity createMetaData(
      UUID dataProviderId,
      String providerAssetId,
      AssetMetaDatas payload,
      PrimaryClass primaryClass
  ) {
    final AssetEntity asset = assetJpaRepository
        .findFirstBySymbolIgnoreCaseAndNameIgnoreCaseAndPrimaryClass(
            payload.getAsset().getSymbol(),
            payload.getAsset().getName(),
            primaryClass
        )
        .orElseGet(() -> createAsset(payload, primaryClass));

    final AssetDataProviderMetaDataEntity metaData = new AssetDataProviderMetaDataEntity();
    metaData.setAsset(asset);
    metaData.setDataProviderId(dataProviderId);
    metaData.setProviderAssetId(providerAssetId);
    metaData.setCreatedAt(Instant.now());
    return metaData;
  }

  private AssetEntity createAsset(AssetMetaDatas payload, PrimaryClass primaryClass) {
    final AssetEntity asset = new AssetEntity();
    asset.setId(UUID.randomUUID());
    asset.setSymbol(payload.getAsset().getSymbol());
    asset.setName(payload.getAsset().getName());
    asset.setPrimaryClass(primaryClass);
    asset.setBlacklisted(false);
    asset.setCreatedAt(Instant.now());
    return assetJpaRepository.save(asset);
  }

  private void updateMetaDataIfNeeded(
      AssetDataProviderMetaDataEntity metaData,
      AssetMetaDatas payload,
      PrimaryClass primaryClass
  ) {
    boolean changed = false;
    if (!Objects.equals(metaData.getSymbol(), payload.getAsset().getSymbol())) {
      metaData.setSymbol(payload.getAsset().getSymbol());
      changed = true;
    }
    if (!Objects.equals(metaData.getName(), payload.getAsset().getName())) {
      metaData.setName(payload.getAsset().getName());
      changed = true;
    }
    if (!Objects.equals(metaData.getPrimaryClass(), primaryClass)) {
      metaData.setPrimaryClass(primaryClass);
      changed = true;
    }
    if (!Objects.equals(metaData.getExtraDatas(), payload.getExtraDatasMap())) {
      metaData.setExtraDatas(payload.getExtraDatasMap());
      changed = true;
    }
    if (!Objects.equals(metaData.getToolBox(), payload.getToolBoxMap())) {
      metaData.setToolBox(payload.getToolBoxMap());
      changed = true;
    }

    if (changed || metaData.getId() == null) {
      assetDataProvidersMetadatasJpaRepository.save(metaData);
    }
  }
}
