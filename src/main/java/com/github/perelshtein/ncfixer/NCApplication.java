package com.github.perelshtein.ncfixer;

import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Accordion;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.*;
import java.util.stream.Stream;

public class NCApplication extends Application {
    private static NCApplication app;
    private static Stage stage;
    protected static Flags flags;
    static Task<String> worker;

    public NCApplication() {
        app = this;

        //load flags from file or use defaults
        try(
            FileInputStream inputFile = new FileInputStream("options.dat");
            ObjectInputStream input = new ObjectInputStream(inputFile)
        )
        {
            flags = (Flags) input.readObject();
        }
        catch(IOException | ClassNotFoundException e) {
            System.out.println("error loading options. use defaults");
            flags = new Flags();
        }
    }

    public static NCApplication getApp() {
        return app;
    }

    public static Stage getStage() {
        return stage;
    }

    @Override
    public void start(Stage stage) throws IOException {
        this.stage = stage;
        FXMLLoader fxmlLoader = new FXMLLoader(NCApplication.class.getResource("hello-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 640, 480);
        stage.setTitle("ncFixer");
        stage.setScene(scene);
        stage.show();

        //use loaded flags
        if(flags.deleteHead) ((CheckBox) scene.lookup("#deleteHead")).fire();
        if(flags.deleteComments) ((CheckBox) scene.lookup("#deleteComments")).fire();
        if(flags.isoMode) ((CheckBox) scene.lookup("#isoMode")).fire();
        if(flags.deleteAbsMove) ((CheckBox) scene.lookup("#deleteAbsMove")).fire();
        if(flags.fastMoveAfterDrill) ((CheckBox) scene.lookup("#fastMoveAfterDrill")).fire();
        if(flags.coolant) ((CheckBox) scene.lookup("#coolant")).fire();
        if(flags.inputExt) ((CheckBox) scene.lookup("#inputExt")).fire();
        if(flags.outputExt) ((CheckBox) scene.lookup("#outputExt")).fire();
        if(flags.changeDrillRetract) ((CheckBox) scene.lookup("#drillRetract")).fire();
        if(flags.removeLineNum) ((CheckBox) scene.lookup("#removeLineNum")).fire();
        if(flags.oneTool) ((CheckBox) scene.lookup("#oneTool")).fire();
        if(flags.separateFiles) ((CheckBox) scene.lookup("#separateFiles")).fire();
        if(flags.toolAtEnd) ((CheckBox) scene.lookup("#toolAtEnd")).fire();
        if(!flags.fileName.isEmpty()) stage.setTitle(flags.fileName);
        ((TextField) scene.lookup("#inputExtField")).setText(flags.inputExtString);
        ((TextField) scene.lookup("#outputExtField")).setText(flags.outputExtString);
        ((TextField) scene.lookup("#drillRetractField")).setText(String.valueOf(flags.drillRetract));

        stage.setOnCloseRequest(windowEvent -> onExit());

        //add icon
        InputStream icon = NCApplication.class.getResourceAsStream("mill.png");
        if(icon != null) stage.getIcons().add(new Image(icon));

        //add listeners when text field loses focus
        TextField input = (TextField) scene.lookup("#outputExtField");
        input.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (! isNowFocused) {
                String s = input.getText()
                        .replaceAll("[\\\\/:*?\"<>|\\.]","")
                        .split(" ")[0];

                //if non-empty field, fix errors and update data
                if(!s.isEmpty()) {
                    flags.outputExtString = s;
                    input.setText(s);
                }

                //if empty field, remain unchanged
                else {
                    input.setText(flags.outputExtString);
                }
            }
        });

        TextField input2 = (TextField) scene.lookup("#drillRetractField");
        input2.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (! isNowFocused) {
                String s = input2.getText().replace(",",".");
                Double d = 0.0;
                boolean fail = false;
                try {
                    d = Double.parseDouble(s);
                }
                catch(NumberFormatException e) {
                    fail = true;
                }

                if(fail) input2.setText(String.valueOf(flags.drillRetract));
                else {
                    flags.drillRetract = d;
                    input2.setText(String.valueOf(d));
                }
            }
        });

        TextField input3 = (TextField) scene.lookup("#inputExtField");
        input3.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (! isNowFocused) {
                String[] arr = input3.getText()
                        .replaceAll("[\\\\/:*?\"<>|\\.]","")
                        .split(",?\\s+");

                //if non-empty field, fix errors and update data
                if(!arr[0].isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    Stream.of(arr).forEach(p -> sb.append(p).append(" "));
                    String s = sb.toString().strip();

                    flags.inputExtString = s;
                    input3.setText(s);
                }

                //if empty field, remain unchanged
                else {
                    input3.setText(flags.inputExtString);
                }
            }
        });

    }

    public static void main(String[] args) {
        launch();
    }

    public static void onExit() {
        //save flags to file
        try(
                FileOutputStream outputFile = new FileOutputStream("options.dat");
                ObjectOutputStream output = new ObjectOutputStream(outputFile)
        )
        {
            output.writeObject(NCApplication.flags);
        }
        catch(IOException e) {
            System.out.println("error saving options");
            e.printStackTrace();
        }

        stage.close();
    }

    //reset path and print error
    public static void errorPath(String name) {
        flags.fileName = "";
        stage.setTitle("ncFixer");

        TextArea area = (TextArea) NCApplication.getStage().getScene().lookup("#result");
        area.appendText(String.format("Cannot open file or folder:\n%s\nRemoving path.\n", name));

        Accordion accord = (Accordion) NCApplication.getStage().getScene().lookup("#accordion");
        accord.setExpandedPane(accord.getPanes().get(1));
    }

    //set new path
    public static void setNewPath(String name) {
        flags.fileName = name;
        stage.setTitle(name);
    }
}