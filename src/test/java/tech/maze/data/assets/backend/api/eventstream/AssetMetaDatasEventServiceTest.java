package tech.maze.data.assets.backend.api.eventstream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.protobuf.Value;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.maze.commons.mappers.ProtobufValueMapper;
import tech.maze.data.assets.backend.api.mappers.PrimaryClassDtoMapper;
import tech.maze.data.assets.backend.domain.models.PrimaryClass;
import tech.maze.data.assets.backend.infrastructure.persistence.entities.AssetDataProviderMetaDataEntity;
import tech.maze.data.assets.backend.infrastructure.persistence.entities.AssetEntity;
import tech.maze.data.assets.backend.infrastructure.persistence.repositories.AssetDataProvidersMetadatasJpaRepository;
import tech.maze.data.assets.backend.infrastructure.persistence.repositories.AssetJpaRepository;
import tech.maze.dtos.assets.models.AssetMetaDatas;
import tech.maze.dtos.assets.models.AssetMetaDatasAsset;

@ExtendWith(MockitoExtension.class)
class AssetMetaDatasEventServiceTest {
  @Mock
  private AssetJpaRepository assetJpaRepository;
  @Mock
  private AssetDataProvidersMetadatasJpaRepository assetDataProvidersMetadatasJpaRepository;

  private final ProtobufValueMapper protobufValueMapper =
      Mappers.getMapper(ProtobufValueMapper.class);
  private final PrimaryClassDtoMapper primaryClassDtoMapper = new PrimaryClassDtoMapper();

  @Test
  void createsAssetAndMetaDataWhenUnknownProviderAssetId() {
    final AssetMetaDatasEventService service = new AssetMetaDatasEventService(
        assetJpaRepository,
        assetDataProvidersMetadatasJpaRepository,
        protobufValueMapper,
        primaryClassDtoMapper
    );
    final UUID dataProviderId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    final AssetMetaDatas payload = AssetMetaDatas.newBuilder()
        .setDataProviderId(
            Value.newBuilder().setStringValue(dataProviderId.toString()).build()
        )
        .setAsset(
            AssetMetaDatasAsset.newBuilder()
                .setId("BTC")
                .setSymbol("BTC")
                .setName("Bitcoin")
                .build()
        )
        .setPrimaryClass(tech.maze.dtos.assets.enums.PrimaryClass.CRYPTO)
        .putExtraDatas("k", "v")
        .putToolBox("k", "v")
        .build();

    when(
        assetDataProvidersMetadatasJpaRepository
            .findFirstByDataProviderIdAndProviderAssetId(dataProviderId, "BTC")
    ).thenReturn(Optional.empty());
    when(
        assetJpaRepository.findFirstBySymbolIgnoreCaseAndNameIgnoreCaseAndPrimaryClass(
            "BTC",
            "Bitcoin",
            PrimaryClass.CRYPTO
        )
    ).thenReturn(Optional.empty());
    when(assetJpaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(assetDataProvidersMetadatasJpaRepository.save(any())).thenAnswer(invocation -> {
      AssetDataProviderMetaDataEntity entity = invocation.getArgument(0);
      if (entity.getId() == null) {
        entity.setId(1L);
      }
      return entity;
    });

    service.process(payload);

    final ArgumentCaptor<AssetEntity> assetCaptor = ArgumentCaptor.forClass(AssetEntity.class);
    verify(assetJpaRepository).save(assetCaptor.capture());
    assertThat(assetCaptor.getValue().getSymbol()).isEqualTo("BTC");
    assertThat(assetCaptor.getValue().getName()).isEqualTo("Bitcoin");
    assertThat(assetCaptor.getValue().getPrimaryClass()).isEqualTo(PrimaryClass.CRYPTO);
    assertThat(assetCaptor.getValue().getBlacklisted()).isFalse();
    assertThat(assetCaptor.getValue().getCreatedAt()).isNotNull();

    final ArgumentCaptor<AssetDataProviderMetaDataEntity> metaCaptor =
        ArgumentCaptor.forClass(AssetDataProviderMetaDataEntity.class);
    verify(assetDataProvidersMetadatasJpaRepository).save(metaCaptor.capture());
    assertThat(metaCaptor.getValue().getDataProviderId()).isEqualTo(dataProviderId);
    assertThat(metaCaptor.getValue().getProviderAssetId()).isEqualTo("BTC");
    assertThat(metaCaptor.getValue().getSymbol()).isEqualTo("BTC");
    assertThat(metaCaptor.getValue().getName()).isEqualTo("Bitcoin");
    assertThat(metaCaptor.getValue().getPrimaryClass()).isEqualTo(PrimaryClass.CRYPTO);
    assertThat(metaCaptor.getValue().getExtraDatas()).isEqualTo(Map.of("k", "v"));
    assertThat(metaCaptor.getValue().getToolBox()).isEqualTo(Map.of("k", "v"));
  }

  @Test
  void updatesExistingMetaDataWhenChanged() {
    final AssetMetaDatasEventService service = new AssetMetaDatasEventService(
        assetJpaRepository,
        assetDataProvidersMetadatasJpaRepository,
        protobufValueMapper,
        primaryClassDtoMapper
    );
    final UUID dataProviderId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    final AssetEntity existingAsset = new AssetEntity();
    existingAsset.setId(UUID.randomUUID());
    existingAsset.setSymbol("BTC");
    existingAsset.setName("Bitcoin");
    existingAsset.setPrimaryClass(PrimaryClass.CRYPTO);
    existingAsset.setCreatedAt(Instant.now());
    existingAsset.setBlacklisted(false);

    final AssetDataProviderMetaDataEntity existingMeta = new AssetDataProviderMetaDataEntity();
    existingMeta.setId(42L);
    existingMeta.setAsset(existingAsset);
    existingMeta.setDataProviderId(dataProviderId);
    existingMeta.setProviderAssetId("BTC");
    existingMeta.setSymbol("OLD");
    existingMeta.setName("OLD");
    existingMeta.setPrimaryClass(PrimaryClass.CRYPTO);
    existingMeta.setExtraDatas(Map.of("old", "1"));
    existingMeta.setToolBox(Map.of("old", "1"));

    final AssetMetaDatas payload = AssetMetaDatas.newBuilder()
        .setDataProviderId(
            Value.newBuilder().setStringValue(dataProviderId.toString()).build()
        )
        .setAsset(
            AssetMetaDatasAsset.newBuilder()
                .setId("BTC")
                .setSymbol("BTC")
                .setName("Bitcoin")
                .build()
        )
        .setPrimaryClass(tech.maze.dtos.assets.enums.PrimaryClass.CRYPTO)
        .putExtraDatas("k", "v")
        .putToolBox("k", "v")
        .build();

    when(
        assetDataProvidersMetadatasJpaRepository
            .findFirstByDataProviderIdAndProviderAssetId(dataProviderId, "BTC")
    ).thenReturn(Optional.of(existingMeta));
    when(assetDataProvidersMetadatasJpaRepository.save(any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.process(payload);

    verify(assetDataProvidersMetadatasJpaRepository).save(eq(existingMeta));
    assertThat(existingMeta.getSymbol()).isEqualTo("BTC");
    assertThat(existingMeta.getName()).isEqualTo("Bitcoin");
    assertThat(existingMeta.getExtraDatas()).isEqualTo(Map.of("k", "v"));
    assertThat(existingMeta.getToolBox()).isEqualTo(Map.of("k", "v"));
  }
}
