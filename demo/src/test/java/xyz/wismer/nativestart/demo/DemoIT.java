package xyz.wismer.nativestart.demo;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import xyz.wismer.nativestart.packer.DescriptorBuilder;
import xyz.wismer.nativestart.packer.ExecutableBuilder;
import xyz.wismer.nativestart.packer.OperatingSystem;
import xyz.wismer.nativestart.packer.Packer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DemoIT {
	private static HttpServer server;

	@TempDir
	static File tempDir;

	@BeforeAll
	static void startHttpServer() throws Exception {
		server = HttpServer.create(new InetSocketAddress(8080), 0);
		server.createContext("/", new DemoHandler());
		server.setExecutor(null);
		server.start();
	}

	@AfterAll
	static void stopHttpServer() throws Exception {
		server.stop(0);
	}

	@AfterEach
	void cleanInstallation() throws Exception {
		FileUtils.deleteDirectory(new File(System.getProperty("user.home") + "/.cache/Demo"));
	}

	@Test
	@EnabledOnOs(OS.LINUX)
	void runApplication() throws Exception {
		DescriptorBuilder builder = Packer.descriptorBuilder("Demo", "1.0.0", OperatingSystem.LINUX);
		builder.splash(new File("target/test-classes/splash"), "splash/splash", "splash/")
				.jvm(new File("target/distribution-linux/runtime/"), "runtime/jdk-linux", "runtime/")
				.library(new File("target/demo-1.0.0-SNAPSHOT.jar"))
				.main("xyz.wismer.nativestart.demo.DemoApp")
				.systemProperty("java.library.path", "runtime/lib");

		builder.generate(tempDir, new URL("http://localhost:8080/"), null);

		ExecutableBuilder executableBuilder = Packer.executableBuilder("Demo", "http://localhost:8080/app.json?launcher=${VERSION}&os=${OS}");
		try (OutputStream outputStream = Files.newOutputStream(tempDir.toPath().resolve("Demo"))) {
			executableBuilder.build(OperatingSystem.LINUX, outputStream);
		}

		assertTrue(new File(tempDir, "Demo").setExecutable(true));
		assertEquals(0, new ProcessBuilder(new File(tempDir, "./Demo").getAbsolutePath(), "2").directory(tempDir).start().waitFor());
	}

	@Test
	@EnabledOnOs(OS.LINUX)
	void runSignedApplication() throws Exception {
		DescriptorBuilder builder = Packer.descriptorBuilder("Demo", "1.0.0", OperatingSystem.LINUX);
		builder.splash(new File("target/test-classes/splash"), "splash/splash.tar", "splash/")
				.jvm(new File("target/distribution-linux/runtime/"), "runtime/jdk-linux.tar", "runtime/")
				.library(new File("target/demo-1.0.0-SNAPSHOT.jar"))
				.main("xyz.wismer.nativestart.demo.DemoApp")
				.systemProperty("java.library.path", "runtime/lib");

		KeyPairGenerator keyGen = KeyPairGenerator.getInstance("Ed25519");
		KeyPair pair = keyGen.genKeyPair();

		builder.generate(tempDir, new URL("http://localhost:8080/"), pair.getPrivate());

		ExecutableBuilder executableBuilder = Packer.executableBuilder("Demo", "http://localhost:8080/app.json?launcher=${VERSION}&os=${OS}");
		executableBuilder.setKey(pair.getPublic());
		try (OutputStream outputStream = Files.newOutputStream(tempDir.toPath().resolve("Demo"))) {
			executableBuilder.build(OperatingSystem.LINUX, outputStream);
		}

		assertTrue(new File(tempDir, "Demo").setExecutable(true));
		assertEquals(0, new ProcessBuilder(new File(tempDir, "./Demo").getAbsolutePath(), "2").directory(tempDir).start().waitFor());
	}

	static class DemoHandler implements HttpHandler {

		@Override
		public void handle(HttpExchange t) throws IOException {
			String requestPath = t.getRequestURI().getPath();
			String file = StringUtils.substringAfterLast(requestPath, "/");
			if (requestPath.equals("/app.json")) {
				serveFile(t, tempDir.toPath().resolve("Demo-linux.json"));
			}
			else if (requestPath.startsWith("/splash/splash")) {
				serveFile(t, tempDir.toPath().resolve(file));
			}
			else if (requestPath.startsWith("/runtime/jdk-linux")) {
				serveFile(t, tempDir.toPath().resolve(file));
			}
			else if (requestPath.equals("/demo-1.0.0-SNAPSHOT.jar")) {
				serveFile(t, new File("target/demo-1.0.0-SNAPSHOT.jar").toPath());
			}
			else {
				t.sendResponseHeaders(404, 0);
			}
		}

		private void serveFile(HttpExchange t, Path path) throws IOException {
			t.sendResponseHeaders(200, 0);
			OutputStream os = t.getResponseBody();
			try (InputStream inputStream = Files.newInputStream(path)) {
				IOUtils.copy(inputStream, os);
			}
			os.close();
		}
	}
}
