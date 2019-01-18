package uk.gov.hmcts.reform.sscs.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;
import static uk.gov.hmcts.reform.sscs.ccd.util.CaseDataUtils.*;

import java.util.Collections;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.springframework.web.client.RestClientException;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.document.DocumentUploadClientApi;
import uk.gov.hmcts.reform.document.domain.Document;
import uk.gov.hmcts.reform.document.domain.UploadResponse;
import uk.gov.hmcts.reform.sscs.ccd.domain.SscsCaseData;
import uk.gov.hmcts.reform.sscs.ccd.domain.SscsCaseDetails;
import uk.gov.hmcts.reform.sscs.ccd.service.CcdService;
import uk.gov.hmcts.reform.sscs.idam.IdamTokens;

public class RoboticsJsonUploadServiceTest {

    private static final String DUMMY_SERVICE_AUTHORIZATION_TOKEN = "serviceAuthorization";
    private static final String DUMMY_OAUTH_2_TOKEN = "oauth2Token";

    @Mock
    private DocumentUploadClientApi documentUploadClientApi;

    @Mock
    private AuthTokenGenerator authTokenGenerator;

    @Mock
    private CcdService ccdService;

    @Mock
    private IdamTokens idamTokens;

    @Mock
    private JSONObject roboticsJson;

    private RoboticsJsonUploadService service;

    @Before
    public void setup() {
        initMocks(this);

        service = new RoboticsJsonUploadService(
                documentUploadClientApi,
                authTokenGenerator,
                ccdService);

        given(authTokenGenerator.generate()).willReturn(DUMMY_SERVICE_AUTHORIZATION_TOKEN);
    }

    @Test
    public void willUpdateCaseWithRoboticsJson() {

        UploadResponse uploadResponse = createUploadResponse();
        given(documentUploadClientApi.upload(
                eq(DUMMY_OAUTH_2_TOKEN),
                eq(DUMMY_SERVICE_AUTHORIZATION_TOKEN),
                anyString(),
                any())).willReturn(uploadResponse);

        SscsCaseData caseData = buildCaseData();
        SscsCaseDetails caseDetails = convertCaseDetailsToSscsCaseDetails(buildCaseDetails());
        service
                .updateCaseWithRoboticsJson(
                        roboticsJson,
                        caseData,
                        caseDetails,
                        idamTokens);

        verify(ccdService, times(1)).updateCase(any(), any(), any(), any(), any(), any());
    }

    private UploadResponse createUploadResponse() {
        UploadResponse response = mock(UploadResponse.class);
        UploadResponse.Embedded embedded = mock(UploadResponse.Embedded.class);
        when(response.getEmbedded()).thenReturn(embedded);
        Document document = createDocument();
        when(embedded.getDocuments()).thenReturn(Collections.singletonList(document));
        return response;
    }

    private Document createDocument() {
        Document.Links links = new Document.Links();
        Document.Link link = new Document.Link();
        links.binary = new Document.Link();
        link.href = "some location";
        links.self = link;
        links.binary.href = "some location";

        Document document = new Document();
        document.links = links;
        return document;
    }
}
