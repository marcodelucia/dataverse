package edu.harvard.iq.dataverse.api;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.path.json.JsonPath;
import com.jayway.restassured.response.Response;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.CREATED;
import static javax.ws.rs.core.Response.Status.OK;
import static org.hamcrest.CoreMatchers.equalTo;
import org.junit.Assert;
import static org.junit.Assert.assertTrue;
import org.junit.BeforeClass;
import org.junit.Test;

public class DownloadFilesIT {

    @BeforeClass
    public static void setUpClass() {
        RestAssured.baseURI = UtilIT.getRestAssuredBaseUri();
    }

    /**
     * This test is focused on downloading all files by their version. All files
     * are public.
     */
    @Test
    public void downloadAllFilesByVersion() throws IOException {

        Response createUser = UtilIT.createRandomUser();
        createUser.prettyPrint();
        createUser.then().assertThat()
                .statusCode(OK.getStatusCode());
        String username = UtilIT.getUsernameFromResponse(createUser);
        String apiToken = UtilIT.getApiTokenFromResponse(createUser);

        Response createDataverseResponse = UtilIT.createRandomDataverse(apiToken);
        createDataverseResponse.prettyPrint();
        createDataverseResponse.then().assertThat()
                .statusCode(CREATED.getStatusCode());

        String dataverseAlias = UtilIT.getAliasFromResponse(createDataverseResponse);

        Response createDataset = UtilIT.createRandomDatasetViaNativeApi(dataverseAlias, apiToken);
        createDataset.prettyPrint();
        createDataset.then().assertThat()
                .statusCode(CREATED.getStatusCode());

        Integer datasetId = UtilIT.getDatasetIdFromResponse(createDataset);
        String datasetPid = UtilIT.getDatasetPersistentIdFromResponse(createDataset);

        Path pathtoReadme = Paths.get(Files.createTempDirectory(null) + File.separator + "README.md");
        Files.write(pathtoReadme, "In the beginning...".getBytes());

        Response uploadReadme = UtilIT.uploadFileViaNative(datasetId.toString(), pathtoReadme.toString(), apiToken);
        uploadReadme.prettyPrint();
        uploadReadme.then().assertThat()
                .statusCode(OK.getStatusCode())
                .body("data.files[0].label", equalTo("README.md"));

        Path pathtoContribFile = Paths.get(Files.createTempDirectory(null) + File.separator + "CONTRIBUTING.md");
        Files.write(pathtoContribFile, "Patches welcome!".getBytes());

        Response uploadContribFile = UtilIT.uploadFileViaNative(datasetId.toString(), pathtoContribFile.toString(), apiToken);
        uploadContribFile.prettyPrint();
        uploadContribFile.then().assertThat()
                .statusCode(OK.getStatusCode())
                .body("data.files[0].label", equalTo("CONTRIBUTING.md"));

        // The creator gets the files from the draft.
        Response downloadFiles1 = UtilIT.downloadFiles(datasetPid, apiToken);
        downloadFiles1.then().assertThat()
                .statusCode(OK.getStatusCode());

        HashSet<String> filenamesFound1 = gatherFilenames(downloadFiles1.getBody().asInputStream());

        // Note that a MANIFEST.TXT file is added.
        HashSet<String> expectedFiles1 = new HashSet<>(Arrays.asList("MANIFEST.TXT", "README.md", "CONTRIBUTING.md"));
        Assert.assertEquals(expectedFiles1, filenamesFound1);

        // A guest user can't download unpublished files.
        Response downloadFiles2 = UtilIT.downloadFiles(datasetPid, null);
        downloadFiles2.prettyPrint();
        downloadFiles2.then().assertThat()
                .statusCode(BAD_REQUEST.getStatusCode());

        UtilIT.publishDataverseViaNativeApi(dataverseAlias, apiToken)
                .then().assertThat().statusCode(OK.getStatusCode());

        // Publishing version 1.0.
        UtilIT.publishDatasetViaNativeApi(datasetPid, "major", apiToken)
                .then().assertThat().statusCode(OK.getStatusCode());

        // Now a guest user can download files (published now)
        Response downloadFiles3 = UtilIT.downloadFiles(datasetPid, null);
        downloadFiles3.then().assertThat()
                .statusCode(OK.getStatusCode());

        Path pathtoLicenseFile = Paths.get(Files.createTempDirectory(null) + File.separator + "LICENSE.md");
        Files.write(pathtoLicenseFile, "Apache".getBytes());

        Response uploadLicenseFile = UtilIT.uploadFileViaNative(datasetId.toString(), pathtoLicenseFile.toString(), apiToken);
        uploadLicenseFile.prettyPrint();
        uploadLicenseFile.then().assertThat()
                .statusCode(OK.getStatusCode())
                .body("data.files[0].label", equalTo("LICENSE.md"));

        Response downloadFiles4 = UtilIT.downloadFiles(datasetPid, apiToken);
        downloadFiles4.then().assertThat()
                .statusCode(OK.getStatusCode());

        HashSet<String> filenamesFound2 = gatherFilenames(downloadFiles4.getBody().asInputStream());

        // The creator gets the draft version with an extra file.
        HashSet<String> expectedFiles2 = new HashSet<>(Arrays.asList("LICENSE.md", "MANIFEST.TXT", "README.md", "CONTRIBUTING.md"));
        Assert.assertEquals(expectedFiles2, filenamesFound2);

        Response downloadFiles5 = UtilIT.downloadFiles(datasetPid, null);
        downloadFiles5.then().assertThat()
                .statusCode(OK.getStatusCode());

        HashSet<String> filenamesFound3 = gatherFilenames(downloadFiles5.getBody().asInputStream());

        // A guest user gets the 1.0 version with only 3 files.
        HashSet<String> expectedFiles3 = new HashSet<>(Arrays.asList("MANIFEST.TXT", "README.md", "CONTRIBUTING.md"));
        Assert.assertEquals(expectedFiles3, filenamesFound3);

        // Publishing version 2.0
        UtilIT.publishDatasetViaNativeApi(datasetPid, "major", apiToken)
                .then().assertThat().statusCode(OK.getStatusCode());

        Response downloadFiles6 = UtilIT.downloadFiles(datasetPid, apiToken);
        downloadFiles6.then().assertThat()
                .statusCode(OK.getStatusCode());

        HashSet<String> filenamesFound4 = gatherFilenames(downloadFiles6.getBody().asInputStream());

        // By not specifying a version, the creator gets the latest version. In this case, 2.0 (published) with 4 files.
        HashSet<String> expectedFiles4 = new HashSet<>(Arrays.asList("LICENSE.md", "MANIFEST.TXT", "README.md", "CONTRIBUTING.md"));
        Assert.assertEquals(expectedFiles4, filenamesFound4);

        String datasetVersion = "1.0";
        Response downloadFiles7 = UtilIT.downloadFiles(datasetPid, datasetVersion, apiToken);
        downloadFiles7.then().assertThat()
                .statusCode(OK.getStatusCode());

        HashSet<String> filenamesFound5 = gatherFilenames(downloadFiles7.getBody().asInputStream());

        // Creator specifies the 1.0 version and gets the expected 3 files.
        HashSet<String> expectedFiles5 = new HashSet<>(Arrays.asList("MANIFEST.TXT", "README.md", "CONTRIBUTING.md"));
        Assert.assertEquals(expectedFiles5, filenamesFound5);

        // Add Code of Conduct file
        Path pathtoCocFile = Paths.get(Files.createTempDirectory(null) + File.separator + "CODE_OF_CONDUCT.md");
        Files.write(pathtoCocFile, "Be excellent to each other.".getBytes());

        // This Code of Conduct file will be in version 3.0 once it's published. For now it's a draft.
        Response uploadCocFile = UtilIT.uploadFileViaNative(datasetId.toString(), pathtoCocFile.toString(), apiToken);
        uploadCocFile.prettyPrint();
        uploadCocFile.then().assertThat()
                .statusCode(OK.getStatusCode())
                .body("data.files[0].label", equalTo("CODE_OF_CONDUCT.md"));

        Response downloadFiles8 = UtilIT.downloadFiles(datasetPid, apiToken);
        downloadFiles8.then().assertThat()
                .statusCode(OK.getStatusCode());

        HashSet<String> filenamesFound6 = gatherFilenames(downloadFiles8.getBody().asInputStream());

        // If the creator doesn't specify a version, they get the latest draft with 5 files.
        HashSet<String> expectedFiles6 = new HashSet<>(Arrays.asList("CODE_OF_CONDUCT.md", "LICENSE.md", "MANIFEST.TXT", "README.md", "CONTRIBUTING.md"));
        Assert.assertEquals(expectedFiles6, filenamesFound6);

        String datasetVersionLatestPublished = ":latest-published";
        Response downloadFiles9 = UtilIT.downloadFiles(datasetPid, datasetVersionLatestPublished, apiToken);
        downloadFiles9.then().assertThat()
                .statusCode(OK.getStatusCode());

        HashSet<String> filenamesFound7 = gatherFilenames(downloadFiles9.getBody().asInputStream());

        // The contributor requested "latest published" and got version 3 with 4 files.
        HashSet<String> expectedFiles7 = new HashSet<>(Arrays.asList("LICENSE.md", "MANIFEST.TXT", "README.md", "CONTRIBUTING.md"));
        Assert.assertEquals(expectedFiles7, filenamesFound7);

    }

    /**
     * This test is focused on downloading all files by their version. All files
     * are public.
     */
    @Test
    public void downloadAllFilesRestricted() throws IOException {

        Response createUser = UtilIT.createRandomUser();
        createUser.prettyPrint();
        createUser.then().assertThat()
                .statusCode(OK.getStatusCode());
        String username = UtilIT.getUsernameFromResponse(createUser);
        String apiToken = UtilIT.getApiTokenFromResponse(createUser);

        Response createDataverseResponse = UtilIT.createRandomDataverse(apiToken);
        createDataverseResponse.prettyPrint();
        createDataverseResponse.then().assertThat()
                .statusCode(CREATED.getStatusCode());

        String dataverseAlias = UtilIT.getAliasFromResponse(createDataverseResponse);

        Response createDataset = UtilIT.createRandomDatasetViaNativeApi(dataverseAlias, apiToken);
        createDataset.prettyPrint();
        createDataset.then().assertThat()
                .statusCode(CREATED.getStatusCode());

        Integer datasetId = UtilIT.getDatasetIdFromResponse(createDataset);
        String datasetPid = UtilIT.getDatasetPersistentIdFromResponse(createDataset);

        Path pathToSecrets = Paths.get(Files.createTempDirectory(null) + File.separator + "secrets.md");
        Files.write(pathToSecrets, "The Nobel Prize will be mine.".getBytes());

        Response uploadSecrets = UtilIT.uploadFileViaNative(datasetId.toString(), pathToSecrets.toString(), apiToken);
        uploadSecrets.prettyPrint();
        uploadSecrets.then().assertThat()
                .statusCode(OK.getStatusCode())
                .body("data.files[0].label", equalTo("secrets.md"));

        Long origFileId = JsonPath.from(uploadSecrets.body().asString()).getLong("data.files[0].dataFile.id");

        Response restrictResponse = UtilIT.restrictFile(origFileId.toString(), true, apiToken);
        restrictResponse.prettyPrint();
        restrictResponse.then().assertThat()
                .statusCode(OK.getStatusCode())
                .body("data.message", equalTo("File secrets.md restricted."));

        Response downloadFiles1 = UtilIT.downloadFiles(datasetPid, apiToken);
        downloadFiles1.then().assertThat()
                .statusCode(OK.getStatusCode());

        // The creator can download a restricted file from a draft.
        Assert.assertEquals(new HashSet<>(Arrays.asList("secrets.md", "MANIFEST.TXT")), gatherFilenames(downloadFiles1.getBody().asInputStream()));

        Path pathToReadme = Paths.get(Files.createTempDirectory(null) + File.separator + "README.md");
        Files.write(pathToReadme, "My findings.".getBytes());

        Response uploadReadme = UtilIT.uploadFileViaNative(datasetId.toString(), pathToReadme.toString(), apiToken);
        uploadReadme.prettyPrint();
        uploadReadme.then().assertThat()
                .statusCode(OK.getStatusCode())
                .body("data.files[0].label", equalTo("README.md"));

        Response downloadFiles2 = UtilIT.downloadFiles(datasetPid, apiToken);
        downloadFiles2.then().assertThat()
                .statusCode(OK.getStatusCode());

        // The creator can download a restricted file and an unrestricted file from a draft.
        Assert.assertEquals(new HashSet<>(Arrays.asList("secrets.md", "README.md", "MANIFEST.TXT")), gatherFilenames(downloadFiles2.getBody().asInputStream()));

        UtilIT.publishDataverseViaNativeApi(dataverseAlias, apiToken)
                .then().assertThat().statusCode(OK.getStatusCode());

        UtilIT.publishDatasetViaNativeApi(datasetPid, "major", apiToken)
                .then().assertThat().statusCode(OK.getStatusCode());

        // Now a guest user can download files (published now)
        Response downloadFiles3 = UtilIT.downloadFiles(datasetPid, null);
        downloadFiles3.then().assertThat()
                .statusCode(OK.getStatusCode());

        // The guest can only get the unrestricted file (and the manifest).
        Assert.assertEquals(new HashSet<>(Arrays.asList("README.md", "MANIFEST.TXT")), gatherFilenames(downloadFiles3.getBody().asInputStream()));

    }

    /**
     * This test is focused on downloading all files when tabular files are
     * present (original vs archival).
     */
    @Test
    public void downloadAllFilesTabular() throws IOException {

        Response createUser = UtilIT.createRandomUser();
        createUser.prettyPrint();
        createUser.then().assertThat()
                .statusCode(OK.getStatusCode());
        String username = UtilIT.getUsernameFromResponse(createUser);
        String apiToken = UtilIT.getApiTokenFromResponse(createUser);

        Response createDataverseResponse = UtilIT.createRandomDataverse(apiToken);
        createDataverseResponse.prettyPrint();
        createDataverseResponse.then().assertThat()
                .statusCode(CREATED.getStatusCode());

        String dataverseAlias = UtilIT.getAliasFromResponse(createDataverseResponse);

        Response createDataset = UtilIT.createRandomDatasetViaNativeApi(dataverseAlias, apiToken);
        createDataset.prettyPrint();
        createDataset.then().assertThat()
                .statusCode(CREATED.getStatusCode());

        Integer datasetId = UtilIT.getDatasetIdFromResponse(createDataset);
        String datasetPid = UtilIT.getDatasetPersistentIdFromResponse(createDataset);

        String pathToFile = "scripts/search/data/tabular/50by1000.dta";

        Response uploadTabular = UtilIT.uploadFileViaNative(datasetId.toString(), pathToFile, apiToken);
        uploadTabular.prettyPrint();
        uploadTabular.then().assertThat()
                .statusCode(OK.getStatusCode())
                .body("data.files[0].label", equalTo("50by1000.dta"));

        assertTrue("Failed test if Ingest Lock exceeds max duration " + pathToFile, UtilIT.sleepForLock(datasetId.longValue(), "Ingest", apiToken, UtilIT.MAXIMUM_INGEST_LOCK_DURATION));

        Response downloadFiles1 = UtilIT.downloadFiles(datasetPid, apiToken);
        downloadFiles1.then().assertThat()
                .statusCode(OK.getStatusCode());

        // By default we get the archival version (.tab).
        Assert.assertEquals(new HashSet<>(Arrays.asList("50by1000.tab", "MANIFEST.TXT")), gatherFilenames(downloadFiles1.getBody().asInputStream()));

        Response downloadFiles2 = UtilIT.downloadFiles(datasetPid, UtilIT.DownloadFormat.original, apiToken);
        downloadFiles2.then().assertThat()
                .statusCode(OK.getStatusCode());

        // By passing format=original we get the original version, Stata (.dta) in this case.
        Assert.assertEquals(new HashSet<>(Arrays.asList("50by1000.dta", "MANIFEST.TXT")), gatherFilenames(downloadFiles2.getBody().asInputStream()));
    }

    private HashSet<String> gatherFilenames(InputStream inputStream) throws IOException {
        HashSet<String> filenamesFound = new HashSet<>();
        try (ZipInputStream zipStream = new ZipInputStream(inputStream)) {
            ZipEntry entry = null;
            while ((entry = zipStream.getNextEntry()) != null) {
                String entryName = entry.getName();
                filenamesFound.add(entryName);
                zipStream.closeEntry();
            }
        }
        return filenamesFound;
    }

}
