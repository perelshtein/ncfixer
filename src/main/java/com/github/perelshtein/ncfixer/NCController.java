package com.github.perelshtein.ncfixer;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.*;

import java.io.*;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

public class NCController {
    private FileScanController scanController;
    StringProperty caption = new SimpleStringProperty();
    StringProperty result = new SimpleStringProperty();

    @FXML
    void on_MenusAbout(ActionEvent event) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, "11");
        alert.setHeaderText("Simple string-processing program for CNC machinists");
        VBox box = new VBox();
        Label label = new Label("Maxim Perelshtein, 2022");
        Hyperlink link = new Hyperlink("http://www.github.com/perelshtein");
        box.getChildren().addAll(label, link);

        link.setOnAction(e -> {
            alert.close();
            NCApplication.getApp().getHostServices().showDocument("http://www.github.com/perelshtein");
        });

        alert.getDialogPane().contentProperty().set(box);
        alert.showAndWait();
    }

    @FXML
    void on_MenusExit(ActionEvent event) {
        NCApplication.onExit();
    }

    @FXML
    void on_OpenFile(ActionEvent event) {
        String oldName = NCApplication.flags.fileName;
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open CNC Program File");

        //try to start from previsious opened folder
        if(!oldName.isEmpty()) {
            try {
                    Path parentPath = Paths.get(oldName).getParent();
                    if(parentPath != null) {
                        File parentFile = parentPath.toFile();
                        if (parentFile.exists()) {
                            fileChooser.setInitialDirectory(parentFile);
                        } else {
                            NCApplication.errorPath(parentPath.toString());
                        }
                    }
            }
            catch(InvalidPathException | UnsupportedOperationException e) {
                    NCApplication.errorPath(oldName);
                }
            }

        File file = fileChooser.showOpenDialog(NCApplication.getStage());
        if (file != null) scanFile(file.toPath());
    }

    @FXML
    void on_OpenFolder(ActionEvent event) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        File folder = directoryChooser.showDialog(NCApplication.getStage());
        if (folder == null) return;

        if(folder.exists()) {
            NCApplication.setNewPath(folder.toString());
            scanFolder(folder.toPath());
        }

        else NCApplication.errorPath(folder.toString());
    }

    @FXML
    void on_DeleteHead(ActionEvent event) {
        CheckBox chk = (CheckBox) event.getSource();
        NCApplication.flags.deleteHead = chk.isSelected();
    }

    @FXML
    void on_DeleteComments(ActionEvent event) {
        CheckBox chk = (CheckBox) event.getSource();
        NCApplication.flags.deleteComments = chk.isSelected();
    }

    @FXML
    void on_IsoMode(ActionEvent event) {
        CheckBox chk = (CheckBox) event.getSource();
        NCApplication.flags.isoMode = chk.isSelected();
    }

    @FXML
    void on_DeleteAbsMove(ActionEvent event) {
        CheckBox chk = (CheckBox) event.getSource();
        NCApplication.flags.deleteAbsMove = chk.isSelected();
    }

    @FXML
    void on_Coolant(ActionEvent event) {
        CheckBox chk = (CheckBox) event.getSource();
        NCApplication.flags.coolant = chk.isSelected();
    }

    @FXML
    void on_outputExt(ActionEvent event) {
        CheckBox chk = (CheckBox) event.getSource();
        NCApplication.flags.outputExt = chk.isSelected();

        Scene scene = NCApplication.getStage().getScene();
        TextField input = (TextField) scene.lookup("#outputExtField");
        input.setDisable(!chk.isSelected());
    }

    @FXML
    void on_inputExt(ActionEvent event) {
        CheckBox chk = (CheckBox) event.getSource();
        NCApplication.flags.inputExt = chk.isSelected();

        Scene scene = NCApplication.getStage().getScene();
        TextField input = (TextField) scene.lookup("#inputExtField");
        input.setDisable(!chk.isSelected());
    }

    @FXML
    void on_DrillRetract(ActionEvent event) {
        CheckBox chk = (CheckBox) event.getSource();
        NCApplication.flags.changeDrillRetract = chk.isSelected();

        Scene scene = NCApplication.getStage().getScene();
        TextField input = (TextField) scene.lookup("#drillRetractField");
        input.setDisable(!chk.isSelected());
    }

    @FXML
    void on_OneTool(ActionEvent event) {
        CheckBox chk = (CheckBox) event.getSource();
        NCApplication.flags.oneTool = chk.isSelected();
    }

    @FXML
    void on_SeparateFiles(ActionEvent event) {
        CheckBox chk = (CheckBox) event.getSource();
        NCApplication.flags.separateFiles = chk.isSelected();
    }

    @FXML
    void on_FastMoveAfterDrill(ActionEvent event) {
        CheckBox chk = (CheckBox) event.getSource();
        NCApplication.flags.fastMoveAfterDrill = chk.isSelected();
    }

    @FXML
    void on_RemoveLineNum(ActionEvent event) {
        CheckBox chk = (CheckBox) event.getSource();
        NCApplication.flags.removeLineNum = chk.isSelected();
    }
    @FXML
    void on_ToolAtEnd(ActionEvent event) {
        CheckBox chk = (CheckBox) event.getSource();
        NCApplication.flags.toolAtEnd = chk.isSelected();
    }
    @FXML
    public void on_Rescan(ActionEvent event) {
        String name = NCApplication.flags.fileName;
        if(!name.isEmpty()) {
            Path path = Paths.get(name);
            if(!Files.exists(path)) {
                NCApplication.errorPath(name);
                return;
            }

            if(Files.isRegularFile(path)) scanFile(path);
            else if(Files.isDirectory(path)) scanFolder(path);
        }
    }

    private void scanFile(Path path) {
        //show modal dialog with Stop button
        if(!showProcessingDialog()) return;
        NCApplication.setNewPath(path.toString());

        //label text = path to file
        caption.set(path.toString());

        //make scan in separate thread to make Stop button responsible
        Task<String> task = new Task<>() {
            @Override
            protected String call() {
                FileParser parser = new FileParser();
                parser.parse(path.toString());
                print(parser.result);
                return null;
            }
        };
        NCApplication.worker = task;

        // Task finished anymore. With or without errors.
        // Save messages, Close dialog
        task.setOnSucceeded(p -> {
            Stage dialog = (Stage) NCApplication.getStage().getUserData();
            dialog.fireEvent(new WindowEvent(dialog, WindowEvent.WINDOW_CLOSE_REQUEST));
        });

        bind();
        Thread th = new Thread(task);
        th.setDaemon(true);

        //task cancelled
        task.setOnCancelled(p -> {
            print("Scan stopped\n");
        });

        //start background thread
        th.start();
    }

    private void scanFolder(Path path) {
        //show modal dialog with Stop button
        if (!showProcessingDialog()) return;

        //make scan in separate thread to make Stop button responsible
        Task<String> task = new Task<>() {
            @Override
            protected String call() {
                int cnt = 0;
                try (Stream<Path> stream = Files.walk(path, Integer.MAX_VALUE)) {
                    List<Path> list = stream
                            .filter(file -> !Files.isDirectory(file))
                            .toList();

                    String[] extensions = NCApplication.flags.inputExtString.split(" ");
                    for (Path p : list) {
                        for (String ext : extensions) {
                            if (p.toString().toLowerCase().endsWith(ext.toLowerCase())) {
                                //if stop is not pressed, scan next file
                                if (!isCancelled()) {
                                    cnt++;
                                    caption.set(p.toString() + "\n");

                                    FileParser parser = new FileParser();
                                    if(!parser.parse(p.toString())) throw new InterruptedException();
                                    print(parser.result);

                                    break;
                                } else throw new InterruptedException();
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    print("Scan stopped\n");
                    return null;
                } catch (IOException | InvalidPathException | SecurityException e) {
                    return path.toString();
                }
                if (cnt == 0)
                    print(String.format("No files found with extensions: \"%s\"\n", NCApplication.flags.inputExtString));
                return null;
            }
        };
        NCApplication.worker = task;

        //Task finished. No errors. Close dialog
        task.setOnSucceeded(p -> {
            Stage dialog = (Stage) NCApplication.getStage().getUserData();
            dialog.fireEvent(new WindowEvent(dialog, WindowEvent.WINDOW_CLOSE_REQUEST));
        });

        bind();
        Thread th = new Thread(task);
        th.setDaemon(true);

        //Scan stopped by user
        task.setOnCancelled(p -> {
            th.interrupt();
        });

        //Error catched during scan
        task.setOnFailed(p -> {
            if(task.getException()
                    instanceof IOException |
                    task.getException() instanceof InvalidPathException |
                    task.getException() instanceof SecurityException) {
                try {
                    NCApplication.errorPath(task.get());
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } catch (ExecutionException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        //start background thread
        th.start();
    }

    private boolean showProcessingDialog() {
        //show modal dialog with Stop button
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("file-scan.fxml"));
            Parent parent = loader.load();
            scanController = loader.getController();
            Stage dialog = new Stage();
            dialog.setScene(new Scene(parent));
            dialog.initOwner(NCApplication.getStage());
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initStyle(StageStyle.UTILITY);
            dialog.setOnCloseRequest((event) -> {
                scanController.on_Stop(new ActionEvent());
            });

            //save link to dialog in main stage for later access
            NCApplication.getStage().setUserData(dialog);
            dialog.show();
            return true;
        }
        catch(IOException e) {
            print("Cannot load dialog FXML file");
            return false;
        }
    }

    private void print(String str) {
        if(result.get() == null) result.set(str);
        else result.set(result.get() + str);
        Accordion accord = (Accordion) NCApplication.getStage().getScene().lookup("#accordion");
        accord.setExpandedPane(accord.getPanes().get(1));
    }

    private void bind() {
        //bind properties to controls to see processing status
        Stage childStage = (Stage) NCApplication.getStage().getUserData();
        Text label = (Text) childStage.getScene().lookup("#path");
        label.textProperty().bind(caption);

        TextArea area = (TextArea) NCApplication.getStage().getScene().lookup("#result");
        area.textProperty().bind(result);
    }
}