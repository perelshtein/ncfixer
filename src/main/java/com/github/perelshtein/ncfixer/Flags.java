package com.github.perelshtein.ncfixer;

import java.io.Serializable;

public class Flags implements Serializable {
    private static final long serialVersionUID = 1L;
    boolean deleteHead;
    boolean deleteComments;
    boolean isoMode;
    boolean deleteAbsMove;
    boolean fastMoveAfterDrill;
    boolean coolant;
    boolean inputExt = true;
    boolean outputExt = true;
    String inputExtString = "nc";
    String outputExtString = "ngc";
    boolean changeDrillRetract;
    double drillRetract = 1.5;
    boolean oneTool;
    boolean separateFiles;
    boolean removeLineNum;
    boolean toolAtEnd;
    String fileName = "";
}
