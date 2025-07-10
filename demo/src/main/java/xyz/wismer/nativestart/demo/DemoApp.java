package xyz.wismer.nativestart.demo;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.concurrent.CountDownLatch;

public class DemoApp extends Application {

	private static CountDownLatch stageVisible = new CountDownLatch(1);

	@Override
	public void start(Stage stage) throws Exception {
		Stage.getWindows().addListener((ListChangeListener<Window>) c -> {
			if (!Stage.getWindows().isEmpty()) {
				stageVisible.countDown();
			}
		});

		Parent root = FXMLLoader.load(getClass().getResource("scene.fxml"));

		Scene scene = new Scene(root);
		scene.getStylesheets().add(getClass().getResource("styles.css").toExternalForm());

		stage.setTitle("JavaFX Demo Application");
		stage.setScene(scene);
		stage.show();
	}

	/**
	 * Wait until a stage is visible. Used by NativeStart to show splash while application is loading.
	 */
	public static void awaitUI() {
		try {
			stageVisible.await();
		} catch (InterruptedException e) {
			// ignore
		}
	}

	public static void main(String[] args) {
		if (args.length > 1) {
			int seconds = Integer.parseInt(args[1]);
			new Thread(() -> {
				try {
					Thread.sleep(seconds * 1000);
				} catch (InterruptedException e) {
					// ignore
				}
				Platform.exit();
			}).start();
		}
		launch(DemoApp.class, args);
	}

}
