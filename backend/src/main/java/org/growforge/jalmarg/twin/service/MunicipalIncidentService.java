package org.growforge.jalmarg.twin.service;

import jakarta.annotation.PostConstruct;
import org.growforge.jalmarg.model.Enums.Severity;
import org.growforge.jalmarg.twin.dto.TwinDtos.IncidentAssignRequest;
import org.growforge.jalmarg.twin.dto.TwinDtos.IncidentStatusUpdateRequest;
import org.growforge.jalmarg.twin.dto.TwinDtos.MunicipalIncidentRequest;
import org.growforge.jalmarg.twin.model.AuditEvent;
import org.growforge.jalmarg.twin.model.MunicipalIncident;
import org.growforge.jalmarg.twin.model.MunicipalZone;
import org.growforge.jalmarg.twin.model.ResponseResource;
import org.growforge.jalmarg.twin.model.TwinEnums.IncidentStatus;
import org.growforge.jalmarg.twin.model.TwinEnums.IncidentType;
import org.growforge.jalmarg.twin.model.TwinEnums.ResourceStatus;
import org.growforge.jalmarg.twin.model.TwinEnums.ResourceType;
import org.growforge.jalmarg.twin.repo.AuditEventRepository;
import org.growforge.jalmarg.twin.repo.MunicipalIncidentRepository;
import org.growforge.jalmarg.twin.repo.MunicipalZoneRepository;
import org.growforge.jalmarg.twin.repo.ResponseResourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
public class MunicipalIncidentService {

    private static final Logger log = LoggerFactory.getLogger(MunicipalIncidentService.class);

    private final MunicipalIncidentRepository incidentRepo;
    private final ResponseResourceRepository resourceRepo;
    private final MunicipalZoneRepository zoneRepo;
    private final AuditEventRepository auditRepo;

    public MunicipalIncidentService(MunicipalIncidentRepository incidentRepo,
                                    ResponseResourceRepository resourceRepo,
                                    MunicipalZoneRepository zoneRepo,
                                    AuditEventRepository auditRepo) {
        this.incidentRepo = incidentRepo;
        this.resourceRepo = resourceRepo;
        this.zoneRepo = zoneRepo;
        this.auditRepo = auditRepo;
    }

    @PostConstruct
    @Transactional
    public void seedInitialResources() {
        if (resourceRepo.count() > 0) return;

        List<ResponseResource> resources = List.of(
                new ResponseResource("RES_PUMP_01", ResourceType.MOBILE_DEWATERING_PUMP,
                        "High-Capacity Dewatering Pump Unit #1 (5000 LPM)", 28.6347, 77.2263,
                        ResourceStatus.AVAILABLE, null, "PWD Central Drainage: +91-11-23340001"),

                new ResponseResource("RES_QRT_SOUTH", ResourceType.QUICK_RESPONSE_TEAM,
                        "MCD South Emergency Waterlogging QRT", 28.5400, 77.2200,
                        ResourceStatus.AVAILABLE, null, "MCD Control Room: +91-11-26510002"),

                new ResponseResource("RES_TRAFFIC_POLICE_01", ResourceType.TRAFFIC_POLICE_UNIT,
                        "Delhi Traffic Police Monsoon Diversion Squad #4", 28.6290, 77.2180,
                        ResourceStatus.AVAILABLE, null, "Traffic Control: 1095 / +91-11-25844444")
        );
        resourceRepo.saveAll(resources);
        log.info("Initialized {} municipal emergency response resources.", resources.size());
    }

    @Transactional
    public MunicipalIncident createIncident(MunicipalIncidentRequest req, String actorId, String ip) {
        MunicipalZone zone = null;
        if (req.zoneId() != null) {
            zone = zoneRepo.findById(req.zoneId()).orElse(null);
        }

        String incidentNum = "INC-2026-" + String.format(Locale.ROOT, "%04d", (incidentRepo.count() + 101));

        IncidentType type;
        try {
            type = IncidentType.valueOf(req.incidentType().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            type = IncidentType.WATERLOGGING_CHOKE;
        }

        Severity sev;
        try {
            sev = Severity.valueOf(req.severity().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            sev = Severity.MODERATE;
        }

        String locName = (req.locationName() != null && !req.locationName().isBlank())
                ? req.locationName().trim()
                : (req.title() != null && !req.title().isBlank() ? req.title().trim() : "Delhi NCR Incident Site");

        // Decision-support priority score (0-100)
        int priority = calculatePriority(sev, type, locName);

        MunicipalIncident incident = new MunicipalIncident(
                incidentNum,
                req.title(),
                req.description(),
                type,
                sev,
                IncidentStatus.NEW,
                req.latitude(),
                req.longitude(),
                locName,
                zone,
                null,
                null,
                priority,
                req.relatedReportId()
        );

        MunicipalIncident saved = incidentRepo.save(incident);

        // Audit Trail
        auditRepo.save(new AuditEvent(
                actorId != null ? actorId : "ANONYMOUS_OPERATOR",
                "MUNICIPAL_DISPATCHER",
                "INCIDENT_CREATE",
                "MunicipalIncident",
                saved.getId().toString(),
                ip,
                String.format(Locale.ROOT, "{\"incidentNumber\":\"%s\",\"title\":\"%s\",\"priority\":%d}",
                        saved.getIncidentNumber(), saved.getTitle(), saved.getPriorityScore())
        ));

        log.info("Created Municipal Incident #{} at {} (Priority: {})",
                saved.getIncidentNumber(), saved.getLocationName(), saved.getPriorityScore());
        return saved;
    }

    @Transactional
    public MunicipalIncident assignIncident(Long incidentId, IncidentAssignRequest req, String actorId, String ip) {
        MunicipalIncident incident = incidentRepo.findById(incidentId)
                .orElseThrow(() -> new IllegalArgumentException("Incident not found: " + incidentId));

        incident.setAssignedDepartment(req.assignedDepartment());
        incident.setAssignedTeam(req.assignedTeam());
        incident.setStatus(IncidentStatus.ASSIGNED);
        incident.setUpdatedAt(Instant.now());

        if (req.resourceId() != null) {
            ResponseResource res = resourceRepo.findById(req.resourceId()).orElse(null);
            if (res != null) {
                res.setStatus(ResourceStatus.DEPLOYED);
                res.setAssignedIncidentId(incident.getId());
                res.setLastUpdated(Instant.now());
                resourceRepo.save(res);
            }
        }

        MunicipalIncident saved = incidentRepo.save(incident);

        auditRepo.save(new AuditEvent(
                actorId != null ? actorId : "AUTHORITY_OPERATOR",
                "AUTHORITY_OPERATOR",
                "INCIDENT_ASSIGN",
                "MunicipalIncident",
                saved.getId().toString(),
                ip,
                String.format(Locale.ROOT, "{\"dept\":\"%s\",\"team\":\"%s\",\"resourceId\":%s}",
                        req.assignedDepartment(), req.assignedTeam(), req.resourceId())
        ));

        return saved;
    }

    @Transactional
    public MunicipalIncident updateIncidentStatus(Long incidentId, IncidentStatusUpdateRequest req, String actorId, String ip) {
        MunicipalIncident incident = incidentRepo.findById(incidentId)
                .orElseThrow(() -> new IllegalArgumentException("Incident not found: " + incidentId));

        IncidentStatus newStatus;
        try {
            newStatus = IncidentStatus.valueOf(req.status().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid incident status: " + req.status());
        }

        IncidentStatus oldStatus = incident.getStatus();
        incident.setStatus(newStatus);
        incident.setUpdatedAt(Instant.now());

        if (newStatus == IncidentStatus.RESOLVED || newStatus == IncidentStatus.VERIFIED_CLOSED) {
            incident.setResolvedAt(Instant.now());
            if (req.resolutionNotes() != null) {
                incident.setResolutionNotes(req.resolutionNotes());
            }
        }

        MunicipalIncident saved = incidentRepo.save(incident);

        auditRepo.save(new AuditEvent(
                actorId != null ? actorId : "AUTHORITY_OPERATOR",
                "AUTHORITY_OPERATOR",
                "INCIDENT_STATUS_CHANGE",
                "MunicipalIncident",
                saved.getId().toString(),
                ip,
                String.format(Locale.ROOT, "{\"oldStatus\":\"%s\",\"newStatus\":\"%s\",\"notes\":\"%s\"}",
                        oldStatus, newStatus, req.resolutionNotes() != null ? req.resolutionNotes() : "")
        ));

        return saved;
    }

    @Transactional(readOnly = true)
    public List<MunicipalIncident> getAllIncidents() {
        return incidentRepo.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public MunicipalIncident getIncidentById(Long id) {
        return incidentRepo.findById(id).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<ResponseResource> getAllResources() {
        return resourceRepo.findAll();
    }

    @Transactional(readOnly = true)
    public List<AuditEvent> getRecentAuditEvents() {
        return auditRepo.findTop50ByOrderByEventTimestampDesc();
    }

    private static int calculatePriority(Severity sev, IncidentType type, String locName) {
        int base = switch (sev) {
            case SEVERE -> 85;
            case HIGH -> 65;
            case MODERATE -> 45;
            case LOW -> 20;
        };

        if (type == IncidentType.UNDERPASS_INUNDATION || (locName != null && locName.toLowerCase().contains("underpass"))) {
            base += 10;
        }
        if (type == IncidentType.PUMP_FAILURE) {
            base += 15;
        }
        return Math.min(100, base);
    }
}
