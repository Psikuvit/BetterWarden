package me.psikuvit.betterWarden.core.repo;

import me.psikuvit.betterWarden.core.model.Punishment;
import me.psikuvit.betterWarden.core.model.PunishmentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PunishmentRepository extends JpaRepository<Punishment, Long> {

    Page<Punishment> findByUuidOrderByIssuedAtDesc(String uuid, Pageable pageable);

    List<Punishment> findByUuidAndActiveTrue(String uuid);

    List<Punishment> findByActiveTrue();

    List<Punishment> findByUuidAndTypeAndActiveTrue(String uuid, PunishmentType type);

    long countByUuidAndTypeIn(String uuid, List<PunishmentType> types);

    @Query("SELECT COUNT(p) FROM Punishment p JOIN PunishmentTemplate t ON p.templateId = t.id " +
            "WHERE p.uuid = :uuid AND t.escalationGroup = :group")
    long countByUuidAndTemplateEscalationGroup(@Param("uuid") String uuid, @Param("group") String group);
}
