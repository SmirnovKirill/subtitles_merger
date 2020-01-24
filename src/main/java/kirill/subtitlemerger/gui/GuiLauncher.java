package kirill.subtitlemerger.gui;

import javafx.application.Application;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.TabPane;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import kirill.subtitlemerger.gui.tabs.TabPaneController;
import lombok.extern.apachecommons.CommonsLog;

import java.awt.*;
import java.io.IOException;

@CommonsLog
public class GuiLauncher extends Application {
    private Stage stage;

    public static void main(String[] args) {
        launch();
    }

    @Override
    public void start(Stage stage) throws IOException {
        this.stage = stage;

        GuiContext guiContext = new GuiContext();

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/gui/tabs/tabPane.fxml"));

        TabPane tabPane = loader.load();
        TabPaneController tabPaneController = loader.getController();
        tabPaneController.initialize(stage, guiContext);

        Scene scene = new Scene(tabPane);
        scene.getStylesheets().add("/gui/style.css");
        stage.setScene(scene);

        stage.setResizable(true);
        stage.getIcons().add(new Image(GuiLauncher.class.getResourceAsStream("/gui/icons/icon.png")));
        stage.setTitle("Subtitle merger");
        Application.setUserAgentStylesheet(STYLESHEET_MODENA);

        stage.show();

        SplashScreen splash = SplashScreen.getSplashScreen();
        if (splash != null && splash.isVisible()) {
            splash.close();
        }

        /*
         * I've encountered a very strange behaviour - at first stage's width and height are set to their computed sizes
         * but very soon after this method (start) is called during the startup window sizes are changed for a reason
         * completely unknown to me. Sizes are changed because com.sun.glass.ui.Window::notifyResize is called. This
         * method is called from a native method com.sun.glass.ui.gtk.GtkApplication::_runLoop so I can't understand why
         * that happens. Anyway, I've discovered that if I set stage's width and height explicitly these original sizes
         * will be restored after these weird changes. And it reproduces only in Kubuntu for me, in Windows 10
         * and Arch everything works fine.
         */
        stage.setWidth(stage.getWidth());
        stage.setHeight(stage.getHeight());

        stage.setMinWidth(stage.getWidth());
        stage.setMinHeight(stage.getHeight());
        stage.widthProperty().addListener(this::widthChanged);
    }

    /*
     * This is my workaround for the JavaFX bug described here - https://bugs.openjdk.java.net/browse/JDK-8115476.
     * I've noticed that it happens when width is odd so I decided to ensure that width is always even.
     */
    private void widthChanged(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
        if (newValue.intValue() % 2 == 1) {
            stage.setWidth(newValue.intValue() + 1);
        }
    }
}
