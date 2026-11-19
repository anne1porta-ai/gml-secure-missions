package com.gml.shield.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.gml.shield.dto.CreateMissionRequest;
import com.gml.shield.dto.MissionResponse;
import com.gml.shield.enums.Role;
import com.gml.shield.model.Mission;
import com.gml.shield.model.User;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class MissionMapperTest {

  private static final LocalDateTime SCHEDULED_AT = LocalDateTime.of(2026, 11, 2, 8, 30);

  private final MissionMapper missionMapper = new MissionMapper();
  private final User driver = new User("driver@gml.local", "hash", Role.DRIVER);
  private final User dispatcher = new User("dispatcher@gml.local", "hash", Role.DISPATCHER);

  @Test
  void optionalDetailsAreCopiedToTheMission() {
    Mission mission =
        missionMapper.toEntity(request("  Quai 4  "), "encrypted", driver, dispatcher);

    assertThat(mission.getDescription()).isEqualTo("Quai 4");
    assertThat(mission.getScheduledAt()).isEqualTo(SCHEDULED_AT);
    assertThat(mission.getSecretCode()).isEqualTo("encrypted");
  }

  @Test
  void blankDescriptionIsStoredAsAbsent() {
    Mission mission = missionMapper.toEntity(request("   "), "encrypted", driver, dispatcher);

    assertThat(mission.getDescription()).isNull();
  }

  @Test
  void responseExposesTheDetails() {
    Mission mission = missionMapper.toEntity(request("Quai 4"), "encrypted", driver, dispatcher);

    MissionResponse response = missionMapper.toResponse(mission);

    assertThat(response.description()).isEqualTo("Quai 4");
    assertThat(response.scheduledAt()).isEqualTo(SCHEDULED_AT);
    assertThat(response.driverEmail()).isEqualTo("driver@gml.local");
  }

  private CreateMissionRequest request(String description) {
    return new CreateMissionRequest(
        "Livraison", "Lyon", "secret", "driver@gml.local", description, SCHEDULED_AT);
  }
}
