package com.github.perelshtein.ncfixer;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.TextArea;
import javafx.scene.text.Text;
import javafx.stage.Stage;

public class FileScanController {
    @FXML
    void on_Stop(ActionEvent event) {
        //if thread is still working, stop it
        if(NCApplication.worker != null) {
            NCApplication.worker.cancel();
        }

        //close dialog
        Stage childStage = (Stage) NCApplication.getStage().getUserData();
        childStage.close();
    }
}
