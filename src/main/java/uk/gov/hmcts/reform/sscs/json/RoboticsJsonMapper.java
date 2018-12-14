package uk.gov.hmcts.reform.sscs.json;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.apache.commons.lang.StringUtils;
import org.json.JSONObject;
import org.json.simple.JSONArray;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.sscs.ccd.domain.Address;
import uk.gov.hmcts.reform.sscs.ccd.domain.Appeal;
import uk.gov.hmcts.reform.sscs.ccd.domain.Appellant;
import uk.gov.hmcts.reform.sscs.ccd.domain.Appointee;
import uk.gov.hmcts.reform.sscs.ccd.domain.Contact;
import uk.gov.hmcts.reform.sscs.ccd.domain.ExcludeDate;
import uk.gov.hmcts.reform.sscs.ccd.domain.HearingOptions;
import uk.gov.hmcts.reform.sscs.ccd.domain.Representative;
import uk.gov.hmcts.reform.sscs.ccd.domain.SscsCaseData;
import uk.gov.hmcts.reform.sscs.domain.robotics.RoboticsWrapper;

@Component
public class RoboticsJsonMapper {

    private static final String YES = "Yes";
    private static final String ESA_CASE_CODE = "051DD";
    private static final String PIP_CASE_CODE = "002DD";

    public JSONObject map(RoboticsWrapper roboticsWrapper) {

        SscsCaseData sscsCaseData = roboticsWrapper.getSscsCaseData();

        JSONObject obj = buildAppealDetails(new JSONObject(), sscsCaseData.getAppeal(), roboticsWrapper.getVenueName());

        obj.put("caseId", roboticsWrapper.getCcdCaseId());
        obj.put("evidencePresent", roboticsWrapper.getEvidencePresent());

        if (null != sscsCaseData.getAppeal().getAppellant().getAppointee()) {
            Boolean sameAddressAsAppointee = "Yes".equalsIgnoreCase(sscsCaseData.getAppeal().getAppellant().getIsAddressSameAsAppointee());
            obj.put("appointee", buildAppointeeDetails(sscsCaseData.getAppeal().getAppellant().getAppointee(), sameAddressAsAppointee));
        }

        obj.put("appellant", buildAppellantDetails(sscsCaseData.getAppeal().getAppellant()));

        if (sscsCaseData.getAppeal().getRep() != null && sscsCaseData.getAppeal().getRep().getHasRepresentative().equals("Yes")) {
            obj.put("representative", buildRepresentativeDetails(sscsCaseData.getAppeal().getRep()));
        }

        if (sscsCaseData.getAppeal().getHearingOptions() != null) {
            JSONObject hearingArrangements = buildHearingOptions(sscsCaseData.getAppeal().getHearingOptions());
            if (hearingArrangements.length() > 0) {
                obj.put("hearingArrangements", hearingArrangements);
            }
        }

        return obj;
    }

    private static JSONObject buildAppealDetails(JSONObject obj, Appeal appeal, String venueName) {
        obj.put("caseCode", getCaseCode(appeal.getBenefitType().getCode()));
        obj.put("appellantNino", appeal.getAppellant().getIdentity().getNino());
        obj.put("appellantPostCode", venueName);
        obj.put("appealDate", LocalDate.now().toString());

        if (appeal.getMrnDetails() != null) {
            if (appeal.getMrnDetails().getMrnDate() != null) {
                obj.put("mrnDate", appeal.getMrnDetails().getMrnDate());
            }
            if (appeal.getMrnDetails().getMrnLateReason() != null) {
                obj.put("mrnReasonForBeingLate", appeal.getMrnDetails().getMrnLateReason());
            }
        }

        if (appeal.getMrnDetails().getDwpIssuingOffice() != null) {
            obj.put("pipNumber", appeal.getMrnDetails().getDwpIssuingOffice());
        }

        obj.put("hearingType", convertBooleanToPaperOral(appeal.getHearingOptions().isWantsToAttendHearing()));

        if (appeal.getHearingOptions().isWantsToAttendHearing()) {
            obj.put("hearingRequestParty", appeal.getAppellant().getName().getFullName());
        }

        return obj;
    }

    private static String getCaseCode(String code) {
        if (StringUtils.equalsIgnoreCase("esa", code)) {
            return ESA_CASE_CODE;
        }
        return PIP_CASE_CODE;
    }

    private static JSONObject buildAppellantDetails(Appellant appellant) {
        JSONObject json = new JSONObject();

        json.put("title", appellant.getName().getTitle());
        json.put("firstName", appellant.getName().getFirstName());
        json.put("lastName", appellant.getName().getLastName());

        return buildContactDetails(json, appellant.getAddress(), appellant.getContact());
    }

    private static JSONObject buildAppointeeDetails(Appointee appointee, Boolean sameAddressAsAppointee) {
        JSONObject json = new JSONObject();

        json.put("title", appointee.getName().getTitle());
        json.put("firstName", appointee.getName().getFirstName());
        json.put("lastName", appointee.getName().getLastName());

        json.put("sameAddressAsAppellant", sameAddressAsAppointee ? "Yes" : "No");

        return buildContactDetails(json, appointee.getAddress(), appointee.getContact());
    }

    private static JSONObject buildRepresentativeDetails(Representative rep) {
        JSONObject json = new JSONObject();

        String title = rep.getName().getTitle() != null ? rep.getName().getTitle() : "s/m";
        String firstName = rep.getName().getFirstName() != null ? rep.getName().getFirstName() : ".";
        String lastName = rep.getName().getLastName() != null ? rep.getName().getLastName() : ".";

        json.put("title", title);
        json.put("firstName", firstName);
        json.put("lastName", lastName);

        if (rep.getOrganisation() != null) {
            json.put("organisation", rep.getOrganisation());
        }

        return buildContactDetails(json, rep.getAddress(), rep.getContact());
    }

    @SuppressWarnings("unchecked")
    private static JSONObject buildHearingOptions(HearingOptions hearingOptions) {
        JSONObject hearingArrangements = new JSONObject();

        if (hearingOptions.getArrangements() != null) {

            if (hearingOptions.getLanguageInterpreter() != null && hearingOptions.getLanguageInterpreter().equals(YES) && hearingOptions.getLanguages() != null) {
                hearingArrangements.put("languageInterpreter", hearingOptions.getLanguages());
            }

            if (hearingOptions.wantsSignLanguageInterpreter() && hearingOptions.getSignLanguageType() != null) {
                hearingArrangements.put("signLanguageInterpreter", hearingOptions.getSignLanguageType());
            }

            hearingArrangements.put("hearingLoop", convertBooleanToYesNo(hearingOptions.wantsHearingLoop()));
            hearingArrangements.put("accessibleHearingRoom", convertBooleanToYesNo(hearingOptions.wantsAccessibleHearingRoom()));
        } else if (hearingOptions.getOther() != null || hearingOptions.getExcludeDates() != null) {

            hearingArrangements.put("hearingLoop", convertBooleanToYesNo(false));
            hearingArrangements.put("accessibleHearingRoom", convertBooleanToYesNo(false));
        }

        if (hearingOptions.getOther() != null) {
            hearingArrangements.put("other", hearingOptions.getOther());
        }

        if (hearingOptions.getExcludeDates() != null
                && hearingOptions.getExcludeDates().size() > 0) {
            JSONArray datesCantAttendArray = new JSONArray();
            for (ExcludeDate a : hearingOptions.getExcludeDates()) {
                // Assume start and end date are always the same
                datesCantAttendArray.add(getLocalDate(a.getValue().getStart()));
            }

            hearingArrangements.put("datesCantAttend", datesCantAttendArray);
        }

        return hearingArrangements;
    }

    private static JSONObject buildContactDetails(JSONObject json, Address address, Contact contact) {
        json.put("addressLine1", address.getLine1());

        if (address.getLine2() != null) {
            json.put("addressLine2", address.getLine2());
        }

        json.put("townOrCity", address.getTown());
        json.put("county", address.getCounty());
        json.put("postCode", address.getPostcode());
        json.put("phoneNumber", contact.getMobile());
        json.put("email", contact.getEmail());

        return json;
    }

    private static String convertBooleanToYesNo(Boolean value) {
        return value ? "Yes" : "No";
    }

    private static String convertBooleanToPaperOral(Boolean value) {
        return value ? "Oral" : "Paper";
    }

    private static String getLocalDate(String dateStr) {
        LocalDate localDate = LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        return localDate.toString();
    }
}
