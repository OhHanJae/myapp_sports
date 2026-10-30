package org.gradle.wrapper;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.zip.*;

/**
 * Small offline-compiled bootstrap for developer convenience.
 * This is NOT the Gradle project's official gradle-wrapper.jar. It downloads the
 * configured distribution over HTTPS and launches that distribution unchanged.
 * Replace using `gradle wrapper` whenever the official Gradle distribution is installed.
 */
public final class GradleWrapperMain {
    public static void main(String[] args) throws Exception {
        File root = new File(System.getProperty("user.dir"));
        File propsFile = new File(root, "gradle/wrapper/gradle-wrapper.properties");
        if (!propsFile.isFile()) throw new IOException("Missing gradle-wrapper.properties: " + propsFile);
        Properties props = new Properties();
        try (InputStream in = new FileInputStream(propsFile)) { props.load(in); }
        String url = props.getProperty("distributionUrl");
        if (url == null || !url.startsWith("https://services.gradle.org/distributions/gradle-")) {
            throw new SecurityException("Unexpected Gradle distribution URL");
        }
        String archive = url.substring(url.lastIndexOf('/') + 1);
        String distName = archive.replace("-bin.zip", "").replace("-all.zip", "");
        String userGradle = System.getenv("GRADLE_USER_HOME");
        if (userGradle == null || userGradle.isEmpty()) userGradle = System.getProperty("user.home") + "/.gradle";
        Path base = Path.of(userGradle, "wrapper", "dists", "matchday-bootstrap", distName);
        Path gradleHome = base.resolve(distName);
        String exe = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win") ? "gradle.bat" : "gradle";
        Path gradle = gradleHome.resolve("bin").resolve(exe);
        if (!Files.isRegularFile(gradle)) {
            Files.createDirectories(base);
            Path archiveFile = base.resolve(archive);
            if (!Files.isRegularFile(archiveFile)) {
                System.out.println("Downloading " + url + " (first run only)...");
                HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(30)).build();
                Path temp = base.resolve(archive + ".part");
                HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(10)).GET().build();
                HttpResponse<Path> response = client.send(req, HttpResponse.BodyHandlers.ofFile(temp));
                if (response.statusCode() != 200) throw new IOException("Download HTTP " + response.statusCode());
                Files.move(temp, archiveFile, StandardCopyOption.REPLACE_EXISTING);
            }
            try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archiveFile))) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    Path dest = base.resolve(entry.getName()).normalize();
                    if (!dest.startsWith(base)) throw new SecurityException("Unsafe ZIP path " + entry.getName());
                    if (entry.isDirectory()) Files.createDirectories(dest);
                    else { Files.createDirectories(dest.getParent()); Files.copy(zip, dest, StandardCopyOption.REPLACE_EXISTING); }
                    zip.closeEntry();
                }
            }
            if (!Files.isRegularFile(gradle)) throw new IOException("Gradle archive structure unexpected: " + gradle);
        }
        gradle.toFile().setExecutable(true);
        List<String> command = new ArrayList<>();
        if (exe.endsWith(".bat")) { command.add("cmd.exe"); command.add("/c"); }
        command.add(gradle.toAbsolutePath().toString());
        command.addAll(Arrays.asList(args));
        ProcessBuilder proc = new ProcessBuilder(command).directory(root).inheritIO();
        int exit = proc.start().waitFor();
        System.exit(exit);
    }
}
