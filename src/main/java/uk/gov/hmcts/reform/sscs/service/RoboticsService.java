package uk.gov.hmcts.reform.sscs.service;

import static org.apache.commons.lang3.StringUtils.*;
import static uk.gov.hmcts.reform.sscs.domain.email.EmailAttachment.*;

import java.util.*;

import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.sscs.ccd.domain.Appellant;
import uk.gov.hmcts.reform.sscs.ccd.domain.SscsCaseData;
import uk.gov.hmcts.reform.sscs.ccd.domain.SscsCaseDetails;
import uk.gov.hmcts.reform.sscs.domain.email.EmailAttachment;
import uk.gov.hmcts.reform.sscs.domain.email.RoboticsEmailTemplate;
import uk.gov.hmcts.reform.sscs.domain.robotics.RoboticsWrapper;
import uk.gov.hmcts.reform.sscs.idam.IdamTokens;
import uk.gov.hmcts.reform.sscs.json.RoboticsJsonMapper;
import uk.gov.hmcts.reform.sscs.json.RoboticsJsonValidator;
import uk.gov.hmcts.reform.sscs.model.AirlookupBenefitToVenue;

@Service
@Slf4j
public class RoboticsService {

    private static final String GLASGOW = "GLASGOW";
    private final AirLookupService airLookupService;
    private final EmailService emailService;
    private final RoboticsJsonMapper roboticsJsonMapper;
    private final RoboticsJsonValidator roboticsJsonValidator;
    private final RoboticsEmailTemplate roboticsEmailTemplate;
    private final RoboticsJsonUploadService roboticsJsonUploadService;

    @Autowired
    public RoboticsService(
            AirLookupService airLookupService,
            EmailService emailService,
            RoboticsJsonMapper roboticsJsonMapper,
            RoboticsJsonValidator roboticsJsonValidator,
            RoboticsEmailTemplate roboticsEmailTemplate,
            RoboticsJsonUploadService roboticsJsonUploadService
    ) {
        this.airLookupService = airLookupService;
        this.emailService = emailService;
        this.roboticsJsonMapper = roboticsJsonMapper;
        this.roboticsJsonValidator = roboticsJsonValidator;
        this.roboticsEmailTemplate = roboticsEmailTemplate;
        this.roboticsJsonUploadService = roboticsJsonUploadService;
    }

    public JSONObject sendCaseToRobotics(SscsCaseData caseData, Long caseId, String postcode, byte[] pdf) {
        return sendCaseToRobotics(caseData, caseId, postcode, pdf, Collections.emptyMap());
    }

    public JSONObject sendCaseToRobotics(SscsCaseData caseData, Long caseId, String postcode, byte[] pdf, Map<String, byte[]> additionalEvidence) {
        AirlookupBenefitToVenue venue = airLookupService.lookupAirVenueNameByPostCode(postcode);

        String venueName = caseData.getAppeal().getBenefitType().getCode().equalsIgnoreCase("esa") ? venue.getEsaVenue() : venue.getPipVenue();

        JSONObject roboticsJson = createRobotics(RoboticsWrapper.builder().sscsCaseData(caseData)
                .ccdCaseId(caseId).venueName(venueName).evidencePresent(caseData.getEvidencePresent()).build());

        log.info("Case {} Robotics JSON successfully created for benefit type {}", caseId,
                caseData.getAppeal().getBenefitType().getCode());

        boolean isScottish = Optional.ofNullable(caseData.getRegionalProcessingCenter()).map(f -> equalsIgnoreCase(f.getName(), GLASGOW)).orElse(false);
        sendJsonByEmail(caseData.getAppeal().getAppellant(), roboticsJson, pdf, additionalEvidence, isScottish);
        log.info("Case {} Robotics JSON email sent successfully for benefit type {} isScottish {}", caseId,
                caseData.getAppeal().getBenefitType().getCode(), isScottish);

        return roboticsJson;
    }

    public JSONObject createRobotics(RoboticsWrapper appeal) {

        JSONObject roboticsAppeal = roboticsJsonMapper.map(appeal);

        roboticsJsonValidator.validate(roboticsAppeal);

        return roboticsAppeal;
    }

    public void attachRoboticsJsonToCaseInCcd(JSONObject roboticsJson, SscsCaseData caseData,
                                              IdamTokens idamTokens, SscsCaseDetails caseDetails) {

        log.info("Sending case {} to Robotics", caseDetails.getId());

        if (caseDetails.getId() == null) {
            log.info("CCD caseId is empty - skipping step to update CCD with Robotics JSON");
        } else {
            log.info("CCD caseId is {}, proceeding to update case with Robotics JSON", caseDetails.getId());
            caseData.setCcdCaseId(caseDetails.getId().toString());
            roboticsJsonUploadService
                    .updateCaseWithRoboticsJson(roboticsJson, caseData, caseDetails, idamTokens);
        }
    }

    private void sendJsonByEmail(Appellant appellant, JSONObject json, byte[] pdf, Map<String, byte[]> additionalEvidence, boolean isScottish) {
        log.info("Generating unique email id");
        String appellantUniqueId = emailService.generateUniqueEmailId(appellant);
        log.info("Add default attachments");
        List<EmailAttachment> attachments = addDefaultAttachment(json, pdf, appellantUniqueId);
        log.info("Add additional evidence");
        addAdditionalEvidenceAttachments(additionalEvidence, attachments);
        log.info("Send email");
        emailService.sendEmail(
                roboticsEmailTemplate.generateEmail(
                        appellantUniqueId,
                        attachments,
                        isScottish
                )
        );
    }

    private void addAdditionalEvidenceAttachments(Map<String, byte[]> additionalEvidence, List<EmailAttachment> attachments) {
        for (String filename : additionalEvidence.keySet()) {
            if (filename != null) {
                byte[] content = additionalEvidence.get(filename);
                if (content != null) {
                    attachments.add(file(content, filename));
                }
            }
        }
    }

    private List<EmailAttachment> addDefaultAttachment(JSONObject json, byte[] pdf, String appellantUniqueId) {
        List<EmailAttachment> emailAttachments = new ArrayList<>();

        emailAttachments.add(json(json.toString().getBytes(), appellantUniqueId + ".txt"));

        if (pdf != null) {
            emailAttachments.add(pdf(pdf, appellantUniqueId + ".pdf"));
        }

        return emailAttachments;
    }

}
