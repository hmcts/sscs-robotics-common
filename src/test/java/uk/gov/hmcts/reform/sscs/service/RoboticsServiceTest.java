package uk.gov.hmcts.reform.sscs.service;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;
import static uk.gov.hmcts.reform.sscs.ccd.util.CaseDataUtils.buildCaseData;

import java.util.Collections;
import java.util.List;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import uk.gov.hmcts.reform.sscs.ccd.domain.SscsCaseData;
import uk.gov.hmcts.reform.sscs.domain.email.EmailAttachment;
import uk.gov.hmcts.reform.sscs.domain.email.RoboticsEmailTemplate;
import uk.gov.hmcts.reform.sscs.domain.robotics.RoboticsWrapper;
import uk.gov.hmcts.reform.sscs.json.RoboticsJsonMapper;
import uk.gov.hmcts.reform.sscs.json.RoboticsJsonValidator;
import uk.gov.hmcts.reform.sscs.model.AirlookupBenefitToVenue;

@RunWith(JUnitParamsRunner.class)
public class RoboticsServiceTest {

    private static final boolean NOT_SCOTTISH = false;
    private static final boolean IS_SCOTTISH = !NOT_SCOTTISH;

    @Mock
    private RoboticsJsonMapper roboticsJsonMapper;

    @Mock
    private RoboticsJsonValidator roboticsJsonValidator;

    @Mock
    private AirLookupService airlookupService;

    @Mock
    private EmailService emailService;

    @Mock
    private RoboticsEmailTemplate roboticsEmailTemplate;

    @Mock
    private RoboticsJsonUploadService roboticsJsonUploadService;

    private RoboticsService service;

    @Captor
    private ArgumentCaptor<List<EmailAttachment>> captor;

    @Before
    public void setup() {
        initMocks(this);

        service = new RoboticsService(
                airlookupService,
                emailService,
                roboticsJsonMapper,
                roboticsJsonValidator,
                roboticsEmailTemplate,
                roboticsJsonUploadService);
    }

    @Test
    public void createValidRoboticsAndReturnAsJsonObject() {

        RoboticsWrapper appeal =
            RoboticsWrapper
                .builder()
                .sscsCaseData(buildCaseData())
                .ccdCaseId(123L).venueName("Bromley")
                .build();

        JSONObject mappedJson = mock(JSONObject.class);

        given(roboticsJsonMapper.map(appeal)).willReturn(mappedJson);

        JSONObject actualRoboticsJson = service.createRobotics(appeal);

        then(roboticsJsonMapper).should(times(1)).map(appeal);
        then(roboticsJsonValidator).should(times(1)).validate(mappedJson);

        assertEquals(mappedJson, actualRoboticsJson);
    }

    @Test
    @Parameters({"CARDIFF", "GLASGOW", "", "null"})
    public void generatingRoboticsSendsAnEmail(String rpcName) {

        SscsCaseData appeal = buildCaseData().toBuilder().regionalProcessingCenter(
                buildCaseData().getRegionalProcessingCenter().toBuilder().name(rpcName.equals("null") ? null : rpcName).build()
        ).build();

        JSONObject mappedJson = mock(JSONObject.class);

        given(roboticsJsonMapper.map(any())).willReturn(mappedJson);

        given(airlookupService.lookupAirVenueNameByPostCode("AB12 XYZ")).willReturn(AirlookupBenefitToVenue.builder().pipVenue("Bristol").build());

        given(emailService.generateUniqueEmailId(appeal.getAppeal().getAppellant())).willReturn("Bloggs_123");

        byte[] pdf = {};

        service.sendCaseToRobotics(appeal, 123L, "AB12 XYZ", pdf);

        boolean isScottish = StringUtils.equalsAnyIgnoreCase(rpcName,"GLASGOW");
        verify(roboticsEmailTemplate).generateEmail(eq("Bloggs_123"), captor.capture(), eq(isScottish));
        List<EmailAttachment> attachmentResult = captor.getValue();

        assertThat(attachmentResult.size(), is(2));
        assertThat(attachmentResult.get(0).getFilename(), is("Bloggs_123.txt"));
        assertThat(attachmentResult.get(1).getFilename(), is("Bloggs_123.pdf"));

        verify(roboticsJsonMapper).map(any());
        verify(roboticsJsonValidator).validate(mappedJson);
        verify(emailService).sendEmail(any());
    }

    @Test
    public void generatingRoboticsWithEmptyPdfSendsAnEmail() {

        SscsCaseData appeal = buildCaseData();

        JSONObject mappedJson = mock(JSONObject.class);

        given(roboticsJsonMapper.map(any())).willReturn(mappedJson);

        given(airlookupService.lookupAirVenueNameByPostCode("AB12 XYZ")).willReturn(AirlookupBenefitToVenue.builder().pipVenue("Bristol").build());

        given(emailService.generateUniqueEmailId(appeal.getAppeal().getAppellant())).willReturn("Bloggs_123");

        service.sendCaseToRobotics(appeal, 123L, "AB12 XYZ", null);

        verify(roboticsEmailTemplate).generateEmail(eq("Bloggs_123"), captor.capture(), eq(NOT_SCOTTISH));
        List<EmailAttachment> attachmentResult = captor.getValue();

        assertThat(attachmentResult.size(), is(1));
        assertThat(attachmentResult.get(0).getFilename(), is("Bloggs_123.txt"));
        verify(roboticsJsonMapper).map(any());
        verify(roboticsJsonValidator).validate(mappedJson);
        verify(emailService).sendEmail(any());
    }

    @Test
    public void generatingRoboticsSendsAnEmailWithAdditionalEvidence() {

        SscsCaseData appeal = buildCaseData();

        JSONObject mappedJson = mock(JSONObject.class);

        given(roboticsJsonMapper.map(any())).willReturn(mappedJson);

        given(airlookupService.lookupAirVenueNameByPostCode("AB12 XYZ")).willReturn(AirlookupBenefitToVenue.builder().pipVenue("Bristol").build());

        given(emailService.generateUniqueEmailId(appeal.getAppeal().getAppellant())).willReturn("Bloggs_123");

        byte[] pdf = {};
        byte[] someFile = {};

        service.sendCaseToRobotics(appeal, 123L, "AB12 XYZ", pdf, Collections.singletonMap("Some Evidence.doc", someFile));

        verify(roboticsEmailTemplate).generateEmail(eq("Bloggs_123"), captor.capture(), eq(NOT_SCOTTISH));
        List<EmailAttachment> attachmentResult = captor.getValue();

        assertThat(attachmentResult.size(), is(3));
        assertThat(attachmentResult.get(0).getFilename(), is("Bloggs_123.txt"));
        assertThat(attachmentResult.get(1).getFilename(), is("Bloggs_123.pdf"));
        assertThat(attachmentResult.get(2).getFilename(), is("Some Evidence.doc"));

        verify(roboticsJsonMapper).map(any());
        verify(roboticsJsonValidator).validate(mappedJson);
        verify(emailService).sendEmail(any());
    }

    @Test
    public void givenAdditionalEvidenceHasEmptyFileName_doNotDownloadAdditionalEvidenceAndStillGenerateRoboticsAndSendEmail() {

        SscsCaseData appeal = buildCaseData();

        JSONObject mappedJson = mock(JSONObject.class);

        given(roboticsJsonMapper.map(any())).willReturn(mappedJson);

        given(airlookupService.lookupAirVenueNameByPostCode("AB12 XYZ")).willReturn(AirlookupBenefitToVenue.builder().pipVenue("Bristol").build());

        given(emailService.generateUniqueEmailId(appeal.getAppeal().getAppellant())).willReturn("Bloggs_123");

        byte[] pdf = {};
        byte[] someFile = {};

        service.sendCaseToRobotics(appeal, 123L, "AB12 XYZ", pdf, Collections.singletonMap(null, someFile));

        verify(roboticsEmailTemplate).generateEmail(eq("Bloggs_123"), captor.capture(), eq(NOT_SCOTTISH));
        List<EmailAttachment> attachmentResult = captor.getValue();

        assertThat(attachmentResult.size(), is(2));
        assertThat(attachmentResult.get(0).getFilename(), is("Bloggs_123.txt"));
        assertThat(attachmentResult.get(1).getFilename(), is("Bloggs_123.pdf"));

        verify(roboticsJsonMapper).map(any());
        verify(roboticsJsonValidator).validate(mappedJson);
        verify(emailService).sendEmail(any());
    }

    @Test
    public void givenAdditionalEvidenceFileIsEmpty_doNotDownloadAdditionalEvidenceAndStillGenerateRoboticsAndSendEmail() {

        SscsCaseData appeal = buildCaseData();

        JSONObject mappedJson = mock(JSONObject.class);

        given(roboticsJsonMapper.map(any())).willReturn(mappedJson);

        given(airlookupService.lookupAirVenueNameByPostCode("AB12 XYZ")).willReturn(AirlookupBenefitToVenue.builder().pipVenue("Bristol").build());

        given(emailService.generateUniqueEmailId(appeal.getAppeal().getAppellant())).willReturn("Bloggs_123");

        byte[] pdf = {};

        service.sendCaseToRobotics(appeal, 123L, "AB12 XYZ", pdf, Collections.singletonMap("Some Evidence.doc", null));

        verify(roboticsEmailTemplate).generateEmail(eq("Bloggs_123"), captor.capture(), eq(NOT_SCOTTISH));
        List<EmailAttachment> attachmentResult = captor.getValue();

        assertThat(attachmentResult.size(), is(2));
        assertThat(attachmentResult.get(0).getFilename(), is("Bloggs_123.txt"));
        assertThat(attachmentResult.get(1).getFilename(), is("Bloggs_123.pdf"));

        verify(roboticsJsonMapper).map(any());
        verify(roboticsJsonValidator).validate(mappedJson);
        verify(emailService).sendEmail(any());
    }

    @Test
    public void generatingRoboticsReturnsTheJson() {

        SscsCaseData appeal = buildCaseData();

        JSONObject mappedJson = mock(JSONObject.class);

        given(roboticsJsonMapper.map(any())).willReturn(mappedJson);

        given(airlookupService.lookupAirVenueNameByPostCode("AB12 XYZ")).willReturn(AirlookupBenefitToVenue.builder().pipVenue("Bristol").build());

        given(emailService.generateUniqueEmailId(appeal.getAppeal().getAppellant())).willReturn("Bloggs_123");

        JSONObject roboticsJson = service.sendCaseToRobotics(appeal, 123L, "AB12 XYZ", null);

        assertThat(roboticsJson, is(mappedJson));
    }
}
