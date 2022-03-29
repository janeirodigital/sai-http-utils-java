package com.janeirodigital.sai.httputils;

import com.janeirodigital.mockwebserver.RequestMatchingFixtureDispatcher;
import com.janeirodigital.sai.rdfutils.RdfUtils;
import com.janeirodigital.sai.rdfutils.SaiRdfException;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;
import okio.Buffer;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.janeirodigital.mockwebserver.DispatcherHelper.*;
import static com.janeirodigital.mockwebserver.MockWebServerHelper.toUrl;
import static com.janeirodigital.sai.httputils.ContentType.TEXT_HTML;
import static com.janeirodigital.sai.httputils.ContentType.TEXT_TURTLE;
import static com.janeirodigital.sai.httputils.HttpHeader.AUTHORIZATION;
import static com.janeirodigital.sai.httputils.HttpHeader.CONTENT_TYPE;
import static com.janeirodigital.sai.httputils.HttpUtils.*;
import static com.janeirodigital.sai.rdfutils.RdfUtils.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

public class HttpUtilsTests {

    private static MockWebServer server;
    private static MockWebServer queuingServer;
    private static OkHttpClient httpClient;

    @BeforeAll
    static void beforeAll() throws SaiHttpException {

        // Initialize request fixtures for the MockWebServer
        RequestMatchingFixtureDispatcher dispatcher = new RequestMatchingFixtureDispatcher();
        // GET application registration in Turtle
        mockOnDelete(dispatcher, "/delete-resource", "204");
        mockOnGet(dispatcher, "/get-document-html", "get-document-html");
        mockOnGet(dispatcher, "/get-image-png", "get-image-png");
        mockOnGet(dispatcher, "/get-rdf-container-ttl", "get-rdf-container-ttl");
        mockOnGet(dispatcher, "/get-rdf-resource-ttl", "get-rdf-resource-ttl");
        mockOnGet(dispatcher, "/not-found", "404");
        mockOnPut(dispatcher, "/put-create-resource", "201");
        mockOnPut(dispatcher, "/put-update-resource", "204");
        mockOnPut(dispatcher, "/put-jsonld-resource", "204");

        // Initialize the Mock Web Server and assign the initialized dispatcher
        server = new MockWebServer();
        server.setDispatcher(dispatcher);
        // Initialize another Mock Web Server used specifically for queuing exceptions and assign a queue dispatcher
        // Initialize HTTP client
        httpClient = new OkHttpClient.Builder().build();
        Logger.getLogger(OkHttpClient.class.getName()).setLevel(Level.FINE);
    }

    @BeforeEach
    void beforeEach() throws IOException {
        queuingServer = new MockWebServer();
        queuingServer.start();
    }

    @AfterEach
    void afterEach() throws IOException {
        queuingServer.shutdown();
    }

    @Test
    @DisplayName("Get a resource")
    void getHTTPResource() throws SaiHttpException {
        Response response = getResource(httpClient, toUrl(server, "/get-document-html"));
        assertTrue(response.isSuccessful());
        response.close();
    }

    @Test
    @DisplayName("Get a required resource")
    void getRequiredHTTPResource() throws SaiHttpException, SaiHttpNotFoundException {
        Response response = getRequiredResource(httpClient, toUrl(server, "/get-document-html"));
        assertTrue(response.isSuccessful());
        response.close();
    }

    @Test
    @DisplayName("Get a missing resource")
    void GetMissingHTTPResource() throws SaiHttpException {
        Response response = getResource(httpClient, toUrl(server, "/not-found"));
        assertFalse(response.isSuccessful());
        assertEquals(404, response.code());
        response.close();
    }

    @Test
    @DisplayName("Get a missing RDF resource")
    void GetMissingRdfResource() throws SaiHttpException {
        Response response = getRdfResource(httpClient, toUrl(server, "/not-found"));
        assertFalse(response.isSuccessful());
        assertEquals(404, response.code());
        response.close();
    }

    @Test
    @DisplayName("Get an RDF resource")
    void getRdfHttpResource() throws SaiHttpException, SaiRdfException, SaiHttpNotFoundException {
        URL url = toUrl(server, "/get-rdf-resource-ttl");
        Response response = getRdfResource(httpClient, url);
        assertTrue(response.isSuccessful());
        Model model = getRdfModelFromResponse(response);
        assertNotNull(model);
        assertNotNull(model.getResource(url.toString()));
        response.close();
    }

    @Test
    @DisplayName("Get a Required RDF resource")
    void getRequiredRdfHttpResource() throws SaiHttpException, SaiRdfException, SaiHttpNotFoundException {
        URL url = toUrl(server, "/get-rdf-resource-ttl");
        Response response = getRequiredRdfResource(httpClient, url);
        assertTrue(response.isSuccessful());
        Model model = getRdfModelFromResponse(response);
        assertNotNull(model);
        assertNotNull(model.getResource(url.toString()));
        response.close();
    }

    @Test
    @DisplayName("Get an RDF resource with headers")
    void getRdfHttpResourceHeaders() throws SaiHttpException, SaiRdfException {
        URL url = toUrl(server, "/get-rdf-resource-ttl");
        Headers headers = setHttpHeader(AUTHORIZATION, "some-token-value");
        Response response = getRdfResource(httpClient, url, headers);
        assertTrue(response.isSuccessful());
        Model model = getRdfModelFromResponse(response);
        assertNotNull(model);
        assertNotNull(model.getResource(url.toString()));
        response.close();
    }

    @Test
    @DisplayName("Fail to get a resource without content-type")
    void FailToGetHttpResourceNoContentType() {
        URL url = toUrl(queuingServer, "/get-rdf-resource-ttl-no-ct");
        queuingServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(getRdfBody()));
        assertThrows(SaiHttpException.class, () -> { getRdfResource(httpClient, url); });
    }

    @Test
    @DisplayName("Fail to get an RDF resource with bad content-type")
    void failToGetRdfHttpResourceBadContentType() {
        URL url = toUrl(queuingServer, "/get-rdf-resource-ttl-coolweb");
        queuingServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "cool/web")
                .setBody(getRdfBody()));
        assertThrows(SaiHttpException.class, () -> { getRdfResource(httpClient, url); });
    }

    @Test
    @DisplayName("Fail to get an RDF resource with non-rdf content-type")
    void failToGetRdfHttpResourceNonRdfContentType() {
        URL url = toUrl(queuingServer, "/get-rdf-resource-ttl-nonrdf");
        queuingServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/octet-stream")
                .setBody(getRdfBody()));
        assertThrows(SaiHttpException.class, () -> { getRdfResource(httpClient, url); });
    }

    @Test
    @DisplayName("Fail to get an RDF resource due to IO issue")
    void failToGetRdfHttpResourceIO() throws SaiHttpException {
        URL url = toUrl(queuingServer, "/get-rdf-resource-ttl-io");
        queuingServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader(CONTENT_TYPE.getValue(), TEXT_TURTLE.getValue())
                .setBody(getRdfBody())
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY));
        Response response = getResource(httpClient, url);
        assertTrue(response.isSuccessful());
        assertThrows(SaiHttpException.class, () -> { getRdfModelFromResponse(response); });
        response.close();
    }

    @Test
    @DisplayName("Fail to get a required resource")
    void FailToGetRequiredMissingHttpResource() {
        queuingServer.enqueue(new MockResponse().setResponseCode(404).setBody(""));
        assertThrows(SaiHttpNotFoundException.class, () -> {
            getRequiredResource(httpClient, toUrl(queuingServer, "/no-resource"));
        });
        queuingServer.enqueue(new MockResponse().setResponseCode(401).setBody(""));
        assertThrows(SaiHttpException.class, () -> {
            getRequiredResource(httpClient, toUrl(queuingServer, "/not-authorized"));
        });
    }

    @Test
    @DisplayName("Fail to get a resource and log details")
    void FailToGetResourceAndLog() throws SaiHttpException {
        queuingServer.enqueue(new MockResponse().setResponseCode(404).setBody(""));
        try(Response response = getResource(httpClient, toUrl(queuingServer, "/no-resource"))) {
            assertNotNull(getResponseFailureMessage(response));
        }
        queuingServer.enqueue(new MockResponse().setResponseCode(404).setBody(""));
        Response response = deleteResource(httpClient, toUrl(queuingServer, "/path/no-resource"));
    }

    @Test
    @DisplayName("Fail to get a resource due to IO issue")
    void FailToGetHttpResourceIO() {
        queuingServer.enqueue(new MockResponse()
                .setBody(new Buffer().write(new byte[4096]))
                .setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));
        assertThrows(SaiHttpException.class, () -> {
            getResource(httpClient, toUrl(queuingServer, "/get-document-io"));
        });
    }

    @Test
    @DisplayName("Update a resource")
    void updateHttpResource() throws SaiHttpException {
        Response response = putResource(httpClient, toUrl(server, "/put-update-resource"), null, getHtmlBody(), TEXT_HTML);
        assertTrue(response.isSuccessful());
        response = putResource(httpClient, toUrl(server, "/put-update-resource"), null, null, TEXT_HTML);
        assertTrue(response.isSuccessful());
    }

    @Test
    @DisplayName("Update an RDF resource")
    void updateRdfHttpResource() throws SaiHttpException, SaiRdfException {
        URL url = toUrl(server, "/put-update-resource");
        Model model = getModelFromString(urlToUri(url), getRdfBody(), TEXT_TURTLE.getValue());
        Resource resource = model.getResource(url + "#project");
        // Update with resource content
        Response response = putRdfResource(httpClient, url, resource, TEXT_TURTLE);
        assertTrue(response.isSuccessful());
        response.close();
        // Update with no resource content (treated as empty body)
        Headers headers = null;
        response = putRdfResource(httpClient, url, null, TEXT_TURTLE, headers);
        assertTrue(response.isSuccessful());
        response.close();
    }

    @Test
    @DisplayName("Update a JSON-LD RDF resource")
    void updateJsonLdHttpResource() throws SaiHttpException, SaiRdfException {
        URL url = toUrl(server, "/put-jsonld-resource");
        Model readableModel = getModelFromString(urlToUri(url), getJsonLdString(url), LD_JSON);
        Resource resource = getResourceFromModel(readableModel, url);
        // Update with resource content
        Response response = putRdfResource(httpClient, url, resource, ContentType.LD_JSON, "");
        assertTrue(response.isSuccessful());
    }

    @Test
    @DisplayName("Fail to update a JSON-LD RDF resource")
    void failToUpdateJsonLdHttpResource() throws SaiRdfException {
        URL url = toUrl(server, "/put-jsonld-resource");
        Model readableModel = getModelFromString(urlToUri(url), getJsonLdString(url), LD_JSON);
        Resource resource = getResourceFromModel(readableModel, url);
        try (MockedStatic<RdfUtils> mockUtils = Mockito.mockStatic(RdfUtils.class, Mockito.CALLS_REAL_METHODS)) {
            mockUtils.when(() -> RdfUtils.getJsonLdStringFromModel(any(Model.class), anyString())).thenThrow(SaiRdfException.class);
            assertThrows(SaiHttpException.class, () -> putRdfResource(httpClient, url, resource, ContentType.LD_JSON, ""));
        }

    }

    @Test
    @DisplayName("Create an RDF container")
    void createRdfContainerHttpResource() throws SaiHttpException, SaiRdfException {
        URL url = toUrl(server, "/put-create-resource");
        Model model = getModelFromString(urlToUri(url), getRdfContainerBody(), TEXT_TURTLE.getValue());
        Resource resource = model.getResource(url + "#project");
        Response response = putRdfContainer(httpClient, url, resource, TEXT_TURTLE);
        assertTrue(response.isSuccessful());
        response.close();
    }

    @Test
    @DisplayName("Fail to update a resource due to IO issue")
    void failToUpdateHttpResourceIO() {
        queuingServer.enqueue(new MockResponse()
                .setBody(new Buffer().write(new byte[4096]))
                .setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));
        assertThrows(SaiHttpException.class, () -> {
            putResource(httpClient, toUrl(queuingServer, "/put-update-resource-io"), null, getHtmlBody(), TEXT_TURTLE);
        });
    }

    @Test
    @DisplayName("Create a resource")
    void createHttpResource() throws SaiHttpException {
        Response response = putResource(httpClient, toUrl(server, "/put-create-resource"), null, getHtmlBody(), TEXT_TURTLE);
        assertTrue(response.isSuccessful());
    }

    @Test
    @DisplayName("Delete a resource")
    void deleteHttpResource() throws SaiHttpException {
        Headers headers = setHttpHeader(AUTHORIZATION, "some-token-value");
        Response response = deleteResource(httpClient, toUrl(server, "/delete-resource"), headers);
        assertTrue(response.isSuccessful());
    }

    @Test
    @DisplayName("Fail to delete a resource due to IO issue")
    void FailToDeleteHttpResourceIO() {
        queuingServer.enqueue(new MockResponse()
                .setBody(new Buffer().write(new byte[4096]))
                .setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));
        assertThrows(SaiHttpException.class, () -> {
            deleteResource(httpClient, toUrl(queuingServer, "/delete-resource-io"));
        });
    }

    @Test
    @DisplayName("Set various HTTP headers")
    void confirmSetHttpHeader() {

        // Add to an empty list of headers
        Headers solo = setHttpHeader(HttpHeader.IF_NONE_MATCH, "*");
        assertEquals(1, solo.size());
        assertEquals("*", solo.get("If-None-Match"));

        // Append to an existing list of headers
        Headers.Builder builder = new Headers.Builder();
        builder.add(CONTENT_TYPE.getValue(), TEXT_HTML.getValue());
        Headers appended = builder.build();
        appended = setHttpHeader(HttpHeader.IF_NONE_MATCH, "*", appended);
        assertEquals(2, appended.size());
        assertEquals("*", appended.get("If-None-Match"));
        assertEquals(TEXT_HTML.getValue(), appended.get("Content-Type"));

        // Ensure duplicates aren't added
        appended = setHttpHeader(HttpHeader.IF_NONE_MATCH, "*", appended);
        assertEquals(2, appended.size());

    }

    @Test
    @DisplayName("Add various HTTP headers")
    void confirmAddHttpHeader() {

        // Add to an empty list of headers
        Headers solo = addHttpHeader(HttpHeader.LINK, LinkRelation.ACL.getValue());
        assertEquals(1, solo.size());
        assertEquals(LinkRelation.ACL.getValue(), solo.get(HttpHeader.LINK.getValue()));

        // Append to an existing list of headers
        Headers.Builder builder = new Headers.Builder();
        builder.add(CONTENT_TYPE.getValue(), TEXT_HTML.getValue());
        Headers appended = builder.build();
        appended = setHttpHeader(HttpHeader.IF_NONE_MATCH, "*", appended);
        assertEquals(2, appended.size());
        assertEquals("*", appended.get(HttpHeader.IF_NONE_MATCH.getValue()));
        assertEquals(TEXT_HTML.getValue(), appended.get(CONTENT_TYPE.getValue()));

        Headers added = addHttpHeader(HttpHeader.LINK, LinkRelation.DESCRIBED_BY.getValue());
        added = addHttpHeader(HttpHeader.LINK, LinkRelation.MANAGED_BY.getValue(), added);
        assertEquals(2, added.size());

    }

    @Test
    @DisplayName("Add Link relation headers")
    void confirmManageLinkRelationHttpHeaders() {

        // Add to an empty list of headers
        Headers relations = addLinkRelationHeader(LinkRelation.TYPE, LDP_BASIC_CONTAINER);
        relations = addLinkRelationHeader(LinkRelation.TYPE, LDP_CONTAINER, relations);
        relations = addLinkRelationHeader(LinkRelation.ACL, "https://some.pod.example/resource.acl", relations);
        assertEquals(3, relations.size());

        Map<String, List<String>> headerMap = relations.toMultimap();
        List<String> linkHeaders = headerMap.get(HttpHeader.LINK.getValue());
        assertTrue(linkHeaders.contains(getLinkRelationString(LinkRelation.TYPE, LDP_BASIC_CONTAINER)));
        assertTrue(linkHeaders.contains(getLinkRelationString(LinkRelation.TYPE, LDP_CONTAINER)));
        assertTrue(linkHeaders.contains(getLinkRelationString(LinkRelation.ACL, "https://some.pod.example/resource.acl")));

    }

    @Test
    @DisplayName("Convert URL to URI")
    void convertUrlToUri() throws MalformedURLException {
        URL url = new URL("http://www.solidproject.org/");
        URI uri = urlToUri(url);
        assertEquals(url, uri.toURL());
    }

    @Test
    @DisplayName("Fail to convert URL to URI - malformed URL")
    void failToConvertUrlToUri() throws MalformedURLException {
        URL url = new URL("http://www.solidproject.org?q=something&something=<something+else>");
        assertThrows(IllegalStateException.class, () -> { urlToUri(url); });
    }

    @Test
    @DisplayName("Get the base of a URL")
    void convertUrlToBase() throws MalformedURLException, SaiHttpException {
        URL onlyQuery = new URL("http://www.solidproject.org/folder/resource?something=value&other=othervalue");
        URL onlyFragment = new URL("http://www.solidproject.org/folder/resource#somefragment");
        URL both = new URL("http://www.solidproject.org/folder/resource#somefragment?something=value");
        URL expected = new URL("http://www.solidproject.org/folder/resource");
        assertEquals(expected, urlToBase(onlyQuery));
        assertEquals(expected, urlToBase(onlyFragment));
        assertEquals(expected, urlToBase(both));
        assertEquals(expected, urlToBase(expected));
    }

    @Test
    @DisplayName("Get HTTP Method by name")
    void testGetHttpMethodByName() {
        HttpMethod putMethod = HttpMethod.get("PUT");
        assertNotNull(putMethod);
        assertEquals(HttpMethod.PUT, putMethod);
    }

    @Test
    @DisplayName("Get HTTP Header by name")
    void testGetHttpHeaderByName() {
        HttpHeader authHeader = HttpHeader.get("Authorization");
        assertNotNull(authHeader);
        assertEquals(AUTHORIZATION, authHeader);
    }

    @Test
    @DisplayName("Fail to get the base of a URL - malformed URL")
    void failToConvertUrlToBase() throws MalformedURLException {
        URL malformed = new URL("http://www.solidproject.org?q=something&something=<something+else>");
        assertThrows(SaiHttpException.class, () -> urlToBase(malformed));
    }


    @Test
    @DisplayName("Convert string to URL")
    void convertStringToUrl() throws SaiHttpException, MalformedURLException {
        URL expected = new URL("http://www.solidproject.org");
        assertEquals(expected, stringToUrl("http://www.solidproject.org"));
    }

    @Test
    @DisplayName("Convert URI to URL")
    void convertUriToUrl() throws SaiHttpException, MalformedURLException {
        URL expected = new URL("http://www.solidproject.org");
        assertEquals(expected, uriToUrl(URI.create("http://www.solidproject.org")));
    }

    @Test
    @DisplayName("Fail to convert string to URL - malformed URL")
    void failToConvertStringToUrl() {
        assertThrows(SaiHttpException.class, () -> stringToUrl("ddd:\\--solidproject_orgZq=something&something=<something+else>"));
    }

    @Test
    @DisplayName("Fail to convert URI to URL - malformed URL")
    void failToConvertUriToUrl() {
        assertThrows(SaiHttpException.class, () -> uriToUrl(URI.create("somescheme://what/path")));
    }

    @Test
    @DisplayName("Add child to URL Path")
    void testAddChildToUrlPath() throws SaiHttpException, MalformedURLException {
        URL base = new URL("http://www.solidproject.org/");
        URL added = addChildToUrlPath(base, "child");
        assertEquals("http://www.solidproject.org/child", added.toString());
    }

    @Test
    @DisplayName("Fail to add child to URL path - malformed URL")
    void failToAddChildToUrlPath() throws MalformedURLException {
        URL base = new URL("http://www.solidproject.org/");
        assertThrows(SaiHttpException.class, () -> addChildToUrlPath(base, "somescheme://what/"));
    }

    private String getHtmlBody() {
        return "<!DOCTYPE html><html><body><h1>Regular HTML Resource</h1></body></html>";
    }

    private String getRdfBody() {
        return "  PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n" +
                "  PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n" +
                "  PREFIX xml: <http://www.w3.org/XML/1998/namespace>\n" +
                "  PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n" +
                "  PREFIX ldp: <http://www.w3.org/ns/ldp#>\n" +
                "  PREFIX ex: <http://www.example.com/ns/ex#>\n" +
                "\n" +
                "  <#project>\n" +
                "    ex:uri </data/projects/project-1/#project> ;\n" +
                "    ex:id 6 ;\n" +
                "    ex:name \"Great Validations\" ;\n" +
                "    ex:created_at \"2021-04-04T20:15:47.000Z\"^^xsd:dateTime ;\n" +
                "    ex:hasMilestone </data/projects/project-1/milestone-3/#milestone> .";
    }

    private String getRdfContainerBody() {
        return "  PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n" +
                "  PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n" +
                "  PREFIX xml: <http://www.w3.org/XML/1998/namespace>\n" +
                "  PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n" +
                "  PREFIX ldp: <http://www.w3.org/ns/ldp#>\n" +
                "  PREFIX ex: <http://www.example.com/ns/ex#>\n" +
                "\n" +
                "  <> ldp:contains </data/projects/project-1/milestone-3/> .\n" +
                "\n" +
                "  <#project>\n" +
                "    ex:uri </data/projects/project-1/#project> ;\n" +
                "    ex:id 6 ;\n" +
                "    ex:name \"Great Validations\" ;\n" +
                "    ex:created_at \"2021-04-04T20:15:47.000Z\"^^xsd:dateTime ;\n" +
                "    ex:hasMilestone </data/projects/project-1/milestone-3/#milestone> .";
    }

    private String getJsonLdString(URL baseUrl) {
        return "{\n" +
                "  \"@context\": {\n" +
                "    \"name\": \"http://schema.org/name\",\n" +
                "    \"id\": \"@id\"\n" +
                "  },\n" +
                "  \"name\": \"Justin B\",\n" +
                "  \"id\": \"" + baseUrl + "\"\n" +
                "}";
    }
    
}
