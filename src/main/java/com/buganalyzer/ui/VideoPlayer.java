package com.buganalyzer.ui;

import com.buganalyzer.model.FileMetadata;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.io.File;

public class VideoPlayer {

    private final FileMetadata fileMetadata;
    private final String projectPath;
    private MediaPlayer mediaPlayer;
    private Label infoLabel;
    private boolean isPlaying = false;
    private static final double FRAME_RATE = 30.0; // Assumed, as JavaFX doesn't provide it easily
    private static final double FRAME_DURATION_MS = 1000.0 / FRAME_RATE;
    private int seekSeconds = 1; // Default seek time

    public VideoPlayer(FileMetadata fileMetadata, String projectPath) {
        this.fileMetadata = fileMetadata;
        this.projectPath = projectPath;
    }

    public Node getView() {
        File videoFile = new File(projectPath, fileMetadata.getFileName());
        Media media = new Media(videoFile.toURI().toString());
        mediaPlayer = new MediaPlayer(media);
        MediaView mediaView = new MediaView(mediaPlayer);

        // Resize media view to fit
        mediaView.setFitWidth(800);
        mediaView.setPreserveRatio(true);

        StackPane root = new StackPane();
        root.getChildren().add(mediaView);

        // Overlay Info
        infoLabel = new Label();
        infoLabel.setStyle("-fx-text-fill: white; -fx-background-color: rgba(0,0,0,0.5); -fx-padding: 5px; -fx-font-size: 14px;");
        StackPane.setAlignment(infoLabel, Pos.TOP_LEFT);
        root.getChildren().add(infoLabel);

        // Controls
        HBox controls = new HBox(10);
        controls.setAlignment(Pos.CENTER);
        controls.setPadding(new Insets(10));
        controls.setStyle("-fx-background-color: rgba(0, 0, 0, 0.5);");
        
        Button playPauseButton = new Button("播放/暂停");
        Button prevFrameButton = new Button("< 帧");
        Button nextFrameButton = new Button("帧 >");
        
        Slider timeSlider = new Slider();
        HBox.setHgrow(timeSlider, Priority.ALWAYS);
        
        Label timeLabel = new Label("00:00 / 00:00");
        timeLabel.setTextFill(Color.WHITE);
        
        controls.getChildren().addAll(prevFrameButton, playPauseButton, nextFrameButton, timeSlider, timeLabel);
        StackPane.setAlignment(controls, Pos.BOTTOM_CENTER);
        root.getChildren().add(controls);

        // Event Handling
        root.setFocusTraversable(true);
        root.setOnKeyPressed(this::handleKeyPress);
        root.setOnMouseClicked(e -> root.requestFocus());

        mediaPlayer.currentTimeProperty().addListener((obs, oldVal, newVal) -> updateInfo());
        mediaPlayer.setOnReady(() -> {
            updateInfo();
            mediaPlayer.play();
            isPlaying = true;
        });

        return root;
    }

    private void handleKeyPress(KeyEvent event) {
        if (event.getCode() == KeyCode.SPACE) {
            if (isPlaying) {
                mediaPlayer.pause();
                isPlaying = false;
            } else {
                mediaPlayer.play();
                isPlaying = true;
            }
        } else if (event.getCode() == KeyCode.LEFT) {
            seek(-seekSeconds);
        } else if (event.getCode() == KeyCode.RIGHT) {
            seek(seekSeconds);
        } else if (event.getCode() == KeyCode.EQUALS || event.getCode() == KeyCode.PLUS || event.getCode() == KeyCode.ADD) {
            // + key (Frame forward)
            mediaPlayer.pause();
            isPlaying = false;
            seekFrame(1);
        } else if (event.getCode() == KeyCode.MINUS || event.getCode() == KeyCode.SUBTRACT) {
            // - key (Frame backward)
            mediaPlayer.pause();
            isPlaying = false;
            seekFrame(-1);
        } else if (event.getCode() == KeyCode.DIGIT1) {
            seekSeconds = 1;
        } else if (event.getCode() == KeyCode.DIGIT3) {
            seekSeconds = 3;
        } else if (event.getCode() == KeyCode.DIGIT5) {
            seekSeconds = 5;
        }
    }

    private void seek(double seconds) {
        mediaPlayer.seek(mediaPlayer.getCurrentTime().add(Duration.seconds(seconds)));
    }

    private void seekFrame(int frames) {
        mediaPlayer.seek(mediaPlayer.getCurrentTime().add(Duration.millis(frames * FRAME_DURATION_MS)));
    }

    private void updateInfo() {
        Duration current = mediaPlayer.getCurrentTime();
        Duration total = mediaPlayer.getTotalDuration();
        
        if (total == null || total.isUnknown()) return;

        long currentSeconds = (long) current.toSeconds();
        long totalSeconds = (long) total.toSeconds();
        long remainingSeconds = totalSeconds - currentSeconds;
        
        // Estimate frame number
        long frame = (long) (current.toMillis() / FRAME_DURATION_MS);
        
        // Format: Time: 12.34s (Frame: 370) | Remaining: 5s
        String text = String.format("Time: %.2fs (Frame: %d) | Remaining: %ds", 
                current.toSeconds(), frame, remainingSeconds);
        
        infoLabel.setText(text);
    }
    
    public void stop() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
        }
    }
}
