package com.workforcehub.plugin.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.plugin.repository.PluginDependencySpec;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.pf4j.PluginDependency;
import org.pf4j.PluginDescriptor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PluginVersionServiceTest {

    private static final String FRONTEND_SRI = "sha384-" + "A".repeat(64);

    @TempDir
    Path tempDir;

    private final PluginVersionService service = new PluginVersionService(
        new ObjectMapper(),
        "1.4.0",
        "1.3.2"
    );

    @Test
    void shouldAcceptCompatiblePluginAndReturnSignedDescriptor() throws Exception {
        Path jarPath = buildJar(pluginJson(
            "2.0.0",
            "1.2.9",
            "1.0.0",
            "1.4.0",
            "17",
            "UI",
            "Workforce Hub",
            FRONTEND_SRI
        ));

        PluginVersionService.PluginVersionDescriptor descriptor = service.checkCompatibility(jarPath);

        assertEquals("version-plugin", descriptor.pluginId());
        assertEquals("2.0.0", descriptor.pluginVersion());
        assertEquals("1.2.9", descriptor.sdkVersion());
        assertEquals("1.0.0", descriptor.minHostVersion());
        assertEquals("1.4.0", descriptor.maxHostVersion());
        assertEquals("17", descriptor.javaVersion());
        assertEquals("UI", descriptor.type());
        assertEquals("Workforce Hub", descriptor.provider());
        assertEquals(FRONTEND_SRI, descriptor.frontendSha384());
    }

    @Test
    void shouldAcceptHelloPluginId() throws Exception {
        Path jarPath = buildJar(
            pluginJson("2.0.0", "1.2.9", "1.0.0", "1.4.0", "17")
                .replace("\"id\":\"version-plugin\"", "\"id\":\"hello-plugin\"")
        );

        PluginVersionService.PluginVersionDescriptor descriptor = service.checkCompatibility(jarPath);

        assertEquals("hello-plugin", descriptor.pluginId());
    }

    @ParameterizedTest
    @MethodSource("invalidPluginIds")
    void shouldRejectPluginIdOutsideStaticPathContract(String pluginId) throws Exception {
        Path jarPath = buildJar(
            pluginJson("2.0.0", "1.2.9", "1.0.0", "1.4.0", "17")
                .replace("\"id\":\"version-plugin\"", "\"id\":\"" + pluginId + "\"")
        );

        assertThrows(
            PluginVersionService.PluginVersionException.class,
            () -> service.checkCompatibility(jarPath)
        );
    }

    private static Stream<String> invalidPluginIds() {
        return Stream.of(
            "../hello-plugin",
            "hello/plugin",
            "Hello-plugin",
            "a".repeat(65)
        );
    }

    @Test
    void shouldAcceptFunctionalPluginWithoutFrontendSri() throws Exception {
        Path jarPath = buildJar(pluginJson("2.0.0", "1.2.9", "1.0.0", "1.4.0", "17"));

        PluginVersionService.PluginVersionDescriptor descriptor = service.checkCompatibility(jarPath);

        assertEquals("FUNCTIONAL", descriptor.type());
        assertEquals("Workforce Hub", descriptor.provider());
        assertNull(descriptor.frontendSha384());
        assertEquals(List.of(), descriptor.dependencies());
    }

    @Test
    void shouldAcceptSortAndReturnSignedDependencies() throws Exception {
        Path jarPath = buildJar(withDependencies(
            pluginJson("2.0.0", "1.2.9", "1.0.0", "1.4.0", "17"),
            """
                [
                  {"pluginId":"z-plugin","minVersion":"1.0.0","maxVersion":"2.0.0"},
                  {"pluginId":"a-plugin","maxVersion":"3.0.0"},
                  {"pluginId":"m-plugin","minVersion":"0.1.0"},
                  {"pluginId":"any-plugin"}
                ]
                """
        ));

        PluginVersionService.PluginVersionDescriptor descriptor = service.checkCompatibility(jarPath);

        assertEquals(List.of(
            new PluginDependencySpec("a-plugin", null, "3.0.0"),
            new PluginDependencySpec("any-plugin", null, null),
            new PluginDependencySpec("m-plugin", "0.1.0", null),
            new PluginDependencySpec("z-plugin", "1.0.0", "2.0.0")
        ), descriptor.dependencies());
    }

    @Test
    void shouldAcceptEmptyAndExactly128Dependencies() throws Exception {
        Path emptyJar = buildJar(withDependencies(
            pluginJson("2.0.0", "1.2.9", "1.0.0", "1.4.0", "17"),
            "[]"
        ));
        String dependencies = IntStream.range(0, 128)
            .mapToObj(index -> "{\"pluginId\":\"dependency-" + index + "\"}")
            .collect(java.util.stream.Collectors.joining(",", "[", "]"));
        Path maximumJar = buildJar(withDependencies(
            pluginJson("2.0.0", "1.2.9", "1.0.0", "1.4.0", "17"),
            dependencies
        ));

        assertEquals(List.of(), service.checkCompatibility(emptyJar).dependencies());
        assertEquals(128, service.checkCompatibility(maximumJar).dependencies().size());
    }

    @Test
    void descriptorShouldDefensivelyCopyAndSortDependencies() {
        List<PluginDependencySpec> dependencies = new ArrayList<>(List.of(
            new PluginDependencySpec("z-plugin", null, null),
            new PluginDependencySpec("a-plugin", null, null)
        ));

        PluginVersionService.PluginVersionDescriptor descriptor = manifest(dependencies);
        dependencies.clear();

        assertEquals(List.of(
            new PluginDependencySpec("a-plugin", null, null),
            new PluginDependencySpec("z-plugin", null, null)
        ), descriptor.dependencies());
        assertThrows(UnsupportedOperationException.class, () -> descriptor.dependencies().clear());
    }

    @ParameterizedTest
    @MethodSource("invalidDependencies")
    void shouldRejectInvalidDependencies(String dependencies) throws Exception {
        Path jarPath = buildJar(withDependencies(
            pluginJson("2.0.0", "1.2.9", "1.0.0", "1.4.0", "17"),
            dependencies
        ));

        assertThrows(
            PluginVersionService.PluginVersionException.class,
            () -> service.checkCompatibility(jarPath)
        );
    }

    private static Stream<String> invalidDependencies() {
        return Stream.of(
            "null",
            "{}",
            "[null]",
            "[\"base-plugin\"]",
            "[{}]",
            "[{\"pluginId\":null}]",
            "[{\"pluginId\":\"Base-plugin\"}]",
            "[{\"pluginId\":\"version-plugin\"}]",
            "[{\"pluginId\":\"base-plugin\",\"optional\":true}]",
            "[{\"pluginId\":\"base-plugin\",\"unknown\":\"value\"}]",
            "[{\"pluginId\":\"base-plugin\"},{\"pluginId\":\"base-plugin\"}]",
            "[{\"pluginId\":\"base-plugin\",\"minVersion\":null}]",
            "[{\"pluginId\":\"base-plugin\",\"minVersion\":\"1.0\"}]",
            "[{\"pluginId\":\"base-plugin\",\"maxVersion\":\"01.0.0\"}]",
            "[{\"pluginId\":\"base-plugin\",\"minVersion\":\"2.0.0\",\"maxVersion\":\"1.0.0\"}]"
        );
    }

    @Test
    void shouldRejectMoreThan128Dependencies() throws Exception {
        String dependencies = IntStream.range(0, 129)
            .mapToObj(index -> "{\"pluginId\":\"dependency-" + index + "\"}")
            .collect(java.util.stream.Collectors.joining(",", "[", "]"));
        Path jarPath = buildJar(withDependencies(
            pluginJson("2.0.0", "1.2.9", "1.0.0", "1.4.0", "17"),
            dependencies
        ));

        assertThrows(
            PluginVersionService.PluginVersionException.class,
            () -> service.checkCompatibility(jarPath)
        );
    }

    @Test
    void shouldValidatePf4jDescriptorIndependentOfDependencyOrder() {
        PluginVersionService.PluginVersionDescriptor manifest = manifest(List.of(
            new PluginDependencySpec("any-plugin", null, null),
            new PluginDependencySpec("base-plugin", "1.0.0", "2.0.0"),
            new PluginDependencySpec("minimum-plugin", "3.0.0", null),
            new PluginDependencySpec("maximum-plugin", null, "4.0.0")
        ));
        PluginDescriptor descriptor = pf4jDescriptor(
            "version-plugin",
            "2.0.0",
            List.of(
                new PluginDependency("minimum-plugin@>=3.0.0"),
                new PluginDependency("base-plugin@>=1.0.0 & <=2.0.0"),
                new PluginDependency("maximum-plugin@<=4.0.0"),
                new PluginDependency("any-plugin")
            )
        );

        assertDoesNotThrow(() -> service.validatePf4jDescriptor(manifest, descriptor));
    }

    @Test
    void shouldRejectPf4jIdentityVersionDependencyRangeAndOptionalDrift() {
        PluginVersionService.PluginVersionDescriptor manifest = manifest(List.of(
            new PluginDependencySpec("base-plugin", "1.0.0", "2.0.0")
        ));

        assertThrows(PluginVersionService.PluginVersionException.class, () ->
            service.validatePf4jDescriptor(
                manifest,
                pf4jDescriptor("other-plugin", "2.0.0", List.of(
                    new PluginDependency("base-plugin@>=1.0.0 & <=2.0.0")
                ))
            ));
        assertThrows(PluginVersionService.PluginVersionException.class, () ->
            service.validatePf4jDescriptor(
                manifest,
                pf4jDescriptor("version-plugin", "2.0.1", List.of(
                    new PluginDependency("base-plugin@>=1.0.0 & <=2.0.0")
                ))
            ));
        assertThrows(PluginVersionService.PluginVersionException.class, () ->
            service.validatePf4jDescriptor(
                manifest,
                pf4jDescriptor("version-plugin", "2.0.0", List.of(
                    new PluginDependency("base-plugin@>=1.0.0&<=2.0.0")
                ))
            ));
        assertThrows(PluginVersionService.PluginVersionException.class, () ->
            service.validatePf4jDescriptor(
                manifest,
                pf4jDescriptor("version-plugin", "2.0.0", List.of(
                    new PluginDependency("base-plugin?@>=1.0.0 & <=2.0.0")
                ))
            ));
        assertThrows(PluginVersionService.PluginVersionException.class, () ->
            service.validatePf4jDescriptor(
                manifest,
                pf4jDescriptor("version-plugin", "2.0.0", List.of())
            ));
        assertThrows(PluginVersionService.PluginVersionException.class, () ->
            service.validatePf4jDescriptor(
                manifest(List.of(
                    new PluginDependencySpec("base-plugin", "1.0.0", "2.0.0"),
                    new PluginDependencySpec("other-plugin", null, null)
                )),
                pf4jDescriptor("version-plugin", "2.0.0", List.of(
                    new PluginDependency("base-plugin@>=1.0.0 & <=2.0.0"),
                    new PluginDependency("base-plugin@>=1.0.0 & <=2.0.0")
                ))
            ));
    }

    @Test
    void shouldMatchDependencyVersionAtInclusiveBoundaries() {
        PluginDependencySpec dependency = new PluginDependencySpec(
            "base-plugin", "1.0.0", "2.0.0"
        );

        assertTrue(service.matchesVersion(dependency, "1.0.0"));
        assertTrue(service.matchesVersion(dependency, "1.5.0"));
        assertTrue(service.matchesVersion(dependency, "2.0.0"));
        assertFalse(service.matchesVersion(dependency, "0.9.9"));
        assertFalse(service.matchesVersion(dependency, "2.0.1"));
        assertTrue(service.matchesVersion(
            new PluginDependencySpec("any-plugin", null, null),
            "99.0.0"
        ));
        assertTrue(service.matchesVersion(
            new PluginDependencySpec("minimum-plugin", "1.0.0", null),
            "1.0.0"
        ));
        assertFalse(service.matchesVersion(
            new PluginDependencySpec("minimum-plugin", "1.0.0", null),
            "0.9.9"
        ));
        assertTrue(service.matchesVersion(
            new PluginDependencySpec("maximum-plugin", null, "2.0.0"),
            "2.0.0"
        ));
        assertFalse(service.matchesVersion(
            new PluginDependencySpec("maximum-plugin", null, "2.0.0"),
            "2.0.1"
        ));
        assertThrows(
            PluginVersionService.PluginVersionException.class,
            () -> service.matchesVersion(dependency, "1.0")
        );
        assertThrows(
            PluginVersionService.PluginVersionException.class,
            () -> service.matchesVersion(dependency, null)
        );
        assertThrows(
            PluginVersionService.PluginVersionException.class,
            () -> service.matchesVersion(
                new PluginDependencySpec("invalid-range", "2.0.0", "1.0.0"),
                "1.5.0"
            )
        );
    }

    @Test
    void shouldRejectMissingPluginType() throws Exception {
        Path jarPath = buildJar(pluginJson(
            "2.0.0",
            "1.2.9",
            "1.0.0",
            "1.4.0",
            "17",
            null,
            "Workforce Hub",
            null
        ));

        assertThrows(
            PluginVersionService.PluginVersionException.class,
            () -> service.checkCompatibility(jarPath)
        );
    }

    @Test
    void shouldRejectMissingPluginProvider() throws Exception {
        Path jarPath = buildJar(pluginJson(
            "2.0.0",
            "1.2.9",
            "1.0.0",
            "1.4.0",
            "17",
            "FUNCTIONAL",
            null,
            null
        ));

        assertThrows(
            PluginVersionService.PluginVersionException.class,
            () -> service.checkCompatibility(jarPath)
        );
    }

    @Test
    void shouldRejectEmptyPluginProvider() throws Exception {
        String json = pluginJson("2.0.0", "1.2.9", "1.0.0", "1.4.0", "17")
            .replace("\"provider\":\"Workforce Hub\"", "\"provider\":\"\"");
        Path jarPath = buildJar(json);

        assertThrows(
            PluginVersionService.PluginVersionException.class,
            () -> service.checkCompatibility(jarPath)
        );
    }

    @Test
    void shouldRejectBlankPluginProvider() throws Exception {
        String json = pluginJson("2.0.0", "1.2.9", "1.0.0", "1.4.0", "17")
            .replace("\"provider\":\"Workforce Hub\"", "\"provider\":\"   \"");
        Path jarPath = buildJar(json);

        assertThrows(
            PluginVersionService.PluginVersionException.class,
            () -> service.checkCompatibility(jarPath)
        );
    }

    @Test
    void shouldRejectNonStringPluginProvider() throws Exception {
        String json = pluginJson("2.0.0", "1.2.9", "1.0.0", "1.4.0", "17")
            .replace("\"provider\":\"Workforce Hub\"", "\"provider\":123");
        Path jarPath = buildJar(json);

        assertThrows(
            PluginVersionService.PluginVersionException.class,
            () -> service.checkCompatibility(jarPath)
        );
    }

    @Test
    void shouldRejectUnsupportedPluginType() throws Exception {
        Path jarPath = buildJar(pluginJson(
            "2.0.0",
            "1.2.9",
            "1.0.0",
            "1.4.0",
            "17",
            "UNKNOWN",
            "Workforce Hub",
            null
        ));

        assertThrows(
            PluginVersionService.PluginVersionException.class,
            () -> service.checkCompatibility(jarPath)
        );
    }

    @Test
    void shouldRejectUiPluginWithoutFrontendSri() throws Exception {
        Path jarPath = buildJar(pluginJson(
            "2.0.0",
            "1.2.9",
            "1.0.0",
            "1.4.0",
            "17",
            "UI",
            "Workforce Hub",
            null
        ));

        assertThrows(
            PluginVersionService.PluginVersionException.class,
            () -> service.checkCompatibility(jarPath)
        );
    }

    @Test
    void shouldRejectUiPluginWithInvalidFrontendSri() throws Exception {
        Path jarPath = buildJar(pluginJson(
            "2.0.0",
            "1.2.9",
            "1.0.0",
            "1.4.0",
            "17",
            "UI",
            "Workforce Hub",
            "sha384-" + "A".repeat(63)
        ));

        assertThrows(
            PluginVersionService.PluginVersionException.class,
            () -> service.checkCompatibility(jarPath)
        );
    }

    @Test
    void shouldRejectFunctionalPluginWithFrontendSri() throws Exception {
        Path jarPath = buildJar(pluginJson(
            "2.0.0",
            "1.2.9",
            "1.0.0",
            "1.4.0",
            "17",
            "FUNCTIONAL",
            "Workforce Hub",
            FRONTEND_SRI
        ));

        assertThrows(
            PluginVersionService.PluginVersionException.class,
            () -> service.checkCompatibility(jarPath)
        );
    }

    @Test
    void shouldRejectFunctionalPluginWithExplicitNullFrontendSri() throws Exception {
        Path jarPath = buildJar(pluginJson(
            "2.0.0",
            "1.2.9",
            "1.0.0",
            "1.4.0",
            "17",
            "FUNCTIONAL",
            "Workforce Hub",
            null,
            true
        ));

        assertThrows(
            PluginVersionService.PluginVersionException.class,
            () -> service.checkCompatibility(jarPath)
        );
    }

    @Test
    void shouldAllowMissingMaximumHostVersion() throws Exception {
        Path jarPath = buildJar(pluginJson("2.0.0", "1.3.2", "1.4.0", null, "17"));

        PluginVersionService.PluginVersionDescriptor descriptor = service.checkCompatibility(jarPath);

        assertNull(descriptor.maxHostVersion());
    }

    @Test
    void shouldAcceptHostVersionAtBothRangeBoundaries() throws Exception {
        Path jarPath = buildJar(pluginJson("2.0.0", "1.3.2", "1.4.0", "1.4.0", "17"));

        PluginVersionService.PluginVersionDescriptor descriptor = service.checkCompatibility(jarPath);

        assertEquals("1.4.0", descriptor.minHostVersion());
        assertEquals("1.4.0", descriptor.maxHostVersion());
    }

    @Test
    void shouldRejectNewerSdkVersion() throws Exception {
        Path jarPath = buildJar(pluginJson("2.0.0", "1.4.0", "1.0.0", null, "17"));

        assertThrows(
            PluginVersionService.PluginVersionException.class,
            () -> service.checkCompatibility(jarPath)
        );
    }

    @Test
    void shouldRejectDifferentSdkMajorVersion() throws Exception {
        Path jarPath = buildJar(pluginJson("2.0.0", "2.0.0", "1.0.0", null, "17"));

        assertThrows(
            PluginVersionService.PluginVersionException.class,
            () -> service.checkCompatibility(jarPath)
        );
    }

    @Test
    void shouldAcceptMatchingZeroMajorSdkMinorWhenPluginIsNotNewer() throws Exception {
        PluginVersionService zeroSdkService = new PluginVersionService(
            new ObjectMapper(),
            "1.4.0",
            "0.1.0"
        );
        Path jarPath = buildJar(pluginJson("2.0.0", "0.1.0", "1.0.0", null, "17"));

        PluginVersionService.PluginVersionDescriptor descriptor =
            zeroSdkService.checkCompatibility(jarPath);

        assertEquals("0.1.0", descriptor.sdkVersion());
    }

    @Test
    void shouldRejectNewerPatchWhenSdkMajorIsZero() throws Exception {
        PluginVersionService zeroSdkService = new PluginVersionService(
            new ObjectMapper(),
            "1.4.0",
            "0.1.0"
        );
        Path jarPath = buildJar(pluginJson("2.0.0", "0.1.1", "1.0.0", null, "17"));

        assertThrows(
            PluginVersionService.PluginVersionException.class,
            () -> zeroSdkService.checkCompatibility(jarPath)
        );
    }

    @Test
    void shouldRejectDifferentMinorWhenSdkMajorIsZeroEvenIfPluginIsOlder() throws Exception {
        PluginVersionService zeroSdkService = new PluginVersionService(
            new ObjectMapper(),
            "1.4.0",
            "0.1.0"
        );
        Path jarPath = buildJar(pluginJson("2.0.0", "0.0.9", "1.0.0", null, "17"));

        assertThrows(
            PluginVersionService.PluginVersionException.class,
            () -> zeroSdkService.checkCompatibility(jarPath)
        );
    }

    @Test
    void shouldRejectNewerMinorWhenSdkMajorIsZero() throws Exception {
        PluginVersionService zeroSdkService = new PluginVersionService(
            new ObjectMapper(),
            "1.4.0",
            "0.1.0"
        );
        Path jarPath = buildJar(pluginJson("2.0.0", "0.2.0", "1.0.0", null, "17"));

        assertThrows(
            PluginVersionService.PluginVersionException.class,
            () -> zeroSdkService.checkCompatibility(jarPath)
        );
    }

    @Test
    void shouldRejectHostBelowMinimumVersion() throws Exception {
        Path jarPath = buildJar(pluginJson("2.0.0", "1.3.0", "1.4.1", null, "17"));

        assertThrows(
            PluginVersionService.PluginVersionException.class,
            () -> service.checkCompatibility(jarPath)
        );
    }

    @Test
    void shouldRejectHostAboveMaximumVersion() throws Exception {
        Path jarPath = buildJar(pluginJson("2.0.0", "1.3.0", "1.0.0", "1.3.9", "17"));

        assertThrows(
            PluginVersionService.PluginVersionException.class,
            () -> service.checkCompatibility(jarPath)
        );
    }

    @Test
    void shouldRejectInvertedHostVersionRange() throws Exception {
        Path jarPath = buildJar(pluginJson("2.0.0", "1.3.0", "1.4.0", "1.3.0", "17"));

        assertThrows(
            PluginVersionService.PluginVersionException.class,
            () -> service.checkCompatibility(jarPath)
        );
    }

    @Test
    void shouldRejectUnsupportedJavaVersion() throws Exception {
        Path jarPath = buildJar(pluginJson("2.0.0", "1.3.0", "1.0.0", null, "99"));

        assertThrows(
            PluginVersionService.PluginVersionException.class,
            () -> service.checkCompatibility(jarPath)
        );
    }

    @Test
    void shouldRejectMalformedSemanticVersion() throws Exception {
        Path jarPath = buildJar(pluginJson("2.0", "1.3.0", "1.0.0", null, "17"));

        assertThrows(
            PluginVersionService.PluginVersionException.class,
            () -> service.checkCompatibility(jarPath)
        );
    }

    @Test
    void shouldRejectMissingPluginJson() throws Exception {
        Path jarPath = tempDir.resolve("missing-plugin-json.jar");
        try (JarOutputStream ignored = new JarOutputStream(Files.newOutputStream(jarPath))) {
        }

        assertThrows(
            PluginVersionService.PluginVersionException.class,
            () -> service.checkCompatibility(jarPath)
        );
    }

    @Test
    void shouldTreatMissingJarAsInfrastructureFailure() {
        Path jarPath = tempDir.resolve("missing-plugin.jar");

        assertThrows(
            IllegalStateException.class,
            () -> service.checkCompatibility(jarPath)
        );
    }

    @Test
    void shouldRejectDuplicatePluginJsonEntries() throws Exception {
        Path jarPath = buildJarWithDuplicatePluginJson(
            pluginJson("2.0.0", "1.3.0", "1.0.0", null, "17")
        );

        assertThrows(
            PluginVersionService.PluginVersionException.class,
            () -> service.checkCompatibility(jarPath)
        );
    }

    @Test
    void shouldRejectTruncatedDeflatedPluginJson() throws Exception {
        Path jarPath = buildJarWithTruncatedDeflatedPluginJson(
            pluginJson("2.0.0", "1.3.0", "1.0.0", null, "17")
        );

        PluginVersionService.PluginVersionException thrown = assertThrows(
            PluginVersionService.PluginVersionException.class,
            () -> service.checkCompatibility(jarPath)
        );
        assertEquals(EOFException.class, thrown.getCause().getClass());
    }

    @Test
    void shouldRejectOversizedPluginJson() throws Exception {
        Path jarPath = buildJar(" ".repeat(64 * 1024 + 1));

        assertThrows(
            PluginVersionService.PluginVersionException.class,
            () -> service.checkCompatibility(jarPath)
        );
    }

    @Test
    void shouldRejectTrailingJsonContent() throws Exception {
        String json = pluginJson("2.0.0", "1.3.0", "1.0.0", null, "17");
        Path jarPath = buildJar(json + json);

        assertThrows(
            PluginVersionService.PluginVersionException.class,
            () -> service.checkCompatibility(jarPath)
        );
    }

    @Test
    void shouldRejectDuplicateJsonFields() throws Exception {
        String json = pluginJson("2.0.0", "1.3.0", "1.0.0", null, "17")
            .replace("\"id\":\"version-plugin\"", "\"id\":\"version-plugin\",\"id\":\"other-plugin\"");
        Path jarPath = buildJar(json);

        assertThrows(
            PluginVersionService.PluginVersionException.class,
            () -> service.checkCompatibility(jarPath)
        );
    }

    private Path buildJar(String pluginJson) throws Exception {
        Path jarPath = tempDir.resolve("plugin-" + System.nanoTime() + ".jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jarPath))) {
            output.putNextEntry(new JarEntry("plugin.json"));
            output.write(pluginJson.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return jarPath;
    }

    private String withDependencies(String pluginJson, String dependencies) {
        int closingBrace = pluginJson.lastIndexOf('}');
        return pluginJson.substring(0, closingBrace)
            + ",\"dependencies\":" + dependencies
            + pluginJson.substring(closingBrace);
    }

    private PluginVersionService.PluginVersionDescriptor manifest(
            List<PluginDependencySpec> dependencies) {
        return new PluginVersionService.PluginVersionDescriptor(
            "version-plugin",
            "2.0.0",
            "1.2.9",
            "1.0.0",
            "1.4.0",
            "17",
            "FUNCTIONAL",
            "Workforce Hub",
            null,
            dependencies
        );
    }

    private PluginDescriptor pf4jDescriptor(
            String pluginId,
            String version,
            List<PluginDependency> dependencies) {
        PluginDescriptor descriptor = mock(PluginDescriptor.class);
        when(descriptor.getPluginId()).thenReturn(pluginId);
        when(descriptor.getVersion()).thenReturn(version);
        when(descriptor.getDependencies()).thenReturn(dependencies);
        return descriptor;
    }

    private Path buildJarWithDuplicatePluginJson(String pluginJson) throws Exception {
        Path jarPath = tempDir.resolve("duplicate-plugin-json.jar");
        byte[] content = pluginJson.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try (ZipArchiveOutputStream output = new ZipArchiveOutputStream(jarPath)) {
            for (int i = 0; i < 2; i++) {
                output.putArchiveEntry(new ZipArchiveEntry("plugin.json"));
                output.write(content);
                output.closeArchiveEntry();
            }
        }
        return jarPath;
    }

    private Path buildJarWithTruncatedDeflatedPluginJson(String pluginJson) throws Exception {
        byte[] content = pluginJson.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        try (DeflaterOutputStream output = new DeflaterOutputStream(
                compressed,
                deflater)) {
            output.write(content);
        } finally {
            deflater.end();
        }
        byte[] compressedBytes = compressed.toByteArray();
        byte[] truncatedBytes = Arrays.copyOf(compressedBytes, compressedBytes.length / 2);
        CRC32 crc = new CRC32();
        crc.update(content);

        ZipArchiveEntry entry = new ZipArchiveEntry("plugin.json");
        entry.setMethod(ZipArchiveEntry.DEFLATED);
        entry.setSize(content.length);
        entry.setCompressedSize(truncatedBytes.length);
        entry.setCrc(crc.getValue());

        Path jarPath = tempDir.resolve("truncated-plugin-json.jar");
        try (ZipArchiveOutputStream output = new ZipArchiveOutputStream(jarPath)) {
            output.addRawArchiveEntry(entry, new ByteArrayInputStream(truncatedBytes));
        }
        return jarPath;
    }

    private String pluginJson(
            String pluginVersion,
            String sdkVersion,
            String minHostVersion,
            String maxHostVersion,
            String javaVersion) {
        return pluginJson(
            pluginVersion,
            sdkVersion,
            minHostVersion,
            maxHostVersion,
            javaVersion,
            "FUNCTIONAL",
            "Workforce Hub",
            null
        );
    }

    private String pluginJson(
            String pluginVersion,
            String sdkVersion,
            String minHostVersion,
            String maxHostVersion,
            String javaVersion,
            String type,
            String provider,
            String frontendSha384) {
        return pluginJson(
            pluginVersion,
            sdkVersion,
            minHostVersion,
            maxHostVersion,
            javaVersion,
            type,
            provider,
            frontendSha384,
            frontendSha384 != null
        );
    }

    private String pluginJson(
            String pluginVersion,
            String sdkVersion,
            String minHostVersion,
            String maxHostVersion,
            String javaVersion,
            String type,
            String provider,
            String frontendSha384,
            boolean includeFrontendSha384) {
        String maxHost = maxHostVersion == null
            ? ""
            : ",\"maxHostVersion\":\"" + maxHostVersion + "\"";
        String pluginType = type == null ? "" : ",\"type\":\"" + type + "\"";
        String pluginProvider = provider == null ? "" : ",\"provider\":\"" + provider + "\"";
        String frontendSri = !includeFrontendSha384
            ? ""
            : frontendSha384 == null
                ? ",\"frontendSha384\":null"
                : ",\"frontendSha384\":\"" + frontendSha384 + "\"";
        return """
            {
              "id":"version-plugin",
              "version":"%s",
              "sdkVersion":"%s",
              "minHostVersion":"%s"%s,
              "javaVersion":"%s"%s%s%s
            }
            """.formatted(
                pluginVersion,
                sdkVersion,
                minHostVersion,
                maxHost,
                javaVersion,
                pluginType,
                pluginProvider,
                frontendSri
            );
    }
}
