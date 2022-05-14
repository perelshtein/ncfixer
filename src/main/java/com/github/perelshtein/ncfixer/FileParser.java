package com.github.perelshtein.ncfixer;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.stream.Stream;

class FileParser {
    public String result = "";
    private ArrayList<String> header = new ArrayList<>();
    private ArrayList<Tool> out = new ArrayList<>();
    private ArrayList<Subprogram> sub = new ArrayList<>();
    private Mode mode = Mode.HEADER;
    private String line;
    private String[] codes;

    public boolean parse(String fileName) {
        long startTime = System.currentTimeMillis();
        var pathIn = Path.of(fileName);
        if (!Files.isRegularFile(pathIn)) {
            result += String.format("Error: %s is not a file\n", fileName);
            return false;
        }

        //read input file and filter strings
        try (
                //safe approach to read non-ASCII symbols, otherwise program will crash
                BufferedReader input = new BufferedReader(new InputStreamReader(new FileInputStream(fileName), StandardCharsets.UTF_8))) {
            int lineCnt = 0;
            boolean isDrill = false;
            boolean isDrillCancel = false;
            boolean stockNameSaved = false;

            while (input.ready()) {
                //read next line,
                //stop immedately if user press Stop
                if (NCApplication.worker.isCancelled()) {
                    result += "Scan stopped\n";
                    return false;
                }

                line = input.readLine().strip();
                codes = line.split("\\s+");
                lineCnt++;

                //remove line numbers?
                if (NCApplication.flags.removeLineNum & codes[0].startsWith("N")) {
                    line = line.replaceAll("N\\d+", "").strip();
                }

                //remove %
                if (NCApplication.flags.deleteHead) {
                    if (line.contains("%")) continue;

                    //remove O0000 in header
                    if (mode == Mode.HEADER & line.matches(".*O\\d+.*")) {
                        line = line.replaceAll("O\\d+", "");
                    }
                }

                //remove comments in header?
                if (mode == Mode.HEADER & line.contains("(")) {
                    if (NCApplication.flags.deleteComments) {

                        //first comment is stock name, save it
                        if (!stockNameSaved) {
                            add(line.substring(line.indexOf("(")));
                            stockNameSaved = true;
                            continue;
                        }

                        //date of file creation, save
                        if (line.contains("DATE")) {
                            add(line);
                            continue;
                        }
                        //tool numbers and tool names, save
                        if (line.matches("\\(\\s*T[0-9]+.*\\)")) add(line);

                        //remove any other comments in header
                        continue;
                    }
                    add(line);
                    continue;
                }

                //On-off ISO Mode?
                //At first, broke out that strings at all
                if (line.contains("G291")) continue;

                //Delete absolute move(G28)?
                if (NCApplication.flags.deleteAbsMove) {
                    if (line.contains("G28")) continue;
                }

                //split input into blocks, one block per tool
                if (getFirstCode().startsWith("T") & line.matches(".*T\\d+.*")) {
                    mode = Mode.TOOL;
                    out.add(new Tool(getFirstCode()));

                    //reset drill search frame
                    isDrill = false;
                    isDrillCancel = false;
                }

                if (mode == Mode.TOOL) {

                    //If in drill cycle
                    if (isDrill) {
                        if (line.contains("G80")) isDrillCancel = true;
                    }

                    //we found start of a new cycle?
                    else if (line.contains("G8") & line.matches(".*G8[1-9].*")) {
                        isDrill = true;
                        isDrillCancel = false;

                        //check drill retract
                        if (NCApplication.flags.changeDrillRetract & line.matches(".*R\\d+.*")) {

                            for (int i = 0; i < codes.length; i++) {
                                if (codes[i].startsWith("R")) {
                                    try {
                                        //replace retract, if it less than N
                                        Double oldRetract = Double.parseDouble(codes[i].substring(1));
                                        if (oldRetract > NCApplication.flags.drillRetract) {
                                            codes[i] = "R" + NCApplication.flags.drillRetract;
                                            rebuildLine();
                                        }
                                    } catch (NumberFormatException e) {
                                        result += String.format("Error parsing retract: line %d of %s\n%s\n", lineCnt, fileName, line);
                                    }
                                    break;
                                }
                            }
                        }
                    }

                    //add G0 after drill?
                    if (isDrillCancel & NCApplication.flags.fastMoveAfterDrill & !line.contains("G28") & line.matches(".*[XY].*")) {
                        isDrillCancel = false;
                        addToFirstCode("G0");
                        rebuildLine();
                    }

                    //Find for subprogram calls
                    if (getFirstCode().startsWith("M98") & line.matches(".*P\\d+.*")) {
                        out.get(out.size() - 1).bSubPrograms = true;
                        out.get(out.size() - 1).subProgramNames.add(getSecondCode().replace("P", "O"));
                    }

                    //Find for coolant turn on
                    if (NCApplication.flags.coolant) {
                        if (line.contains("M") & line.matches(".*M(08|8|13).*")) out.get(out.size() - 1).coolant = true;
                    }
                }

                // Find subprogram
                if (mode != Mode.HEADER & getFirstCode().startsWith("O") & line.matches("O\\d+\\.*")) {
                    mode = Mode.SUBPROGRAM;
                    sub.add(new Subprogram(getFirstCode()));
                }

                add(line);
            }
        } catch (IOException e) {
            result += String.format("Error reading file: %s\n", fileName);
            return false;
        }
        result += String.format("Input parsing OK for %d ms: %s\n", System.currentTimeMillis() - startTime, fileName);

        //change extension?
        String outName = fileName;
        if (NCApplication.flags.outputExt) {
            outName = fileName.substring(0, fileName.lastIndexOf(".") + 1) + NCApplication.flags.outputExtString;
        }

        //write each tool to separate file
        if (NCApplication.flags.separateFiles & out.size() >= 2) {
            int cnt = 0;
            while (cnt < out.size()) {
                String s = outName.substring(0, outName.lastIndexOf("."))
                        + "-"
                        + String.format("%02d", cnt + 1)
                        + outName.substring(fileName.lastIndexOf("."));
                if (!writeOut(s, cnt++)) break;
            }
            return false;
        }

        //write all tools to one file
        else {
            return writeOut(outName, 0);
        }
    }

    private boolean writeOut(String outName, int toolNumber) {
        long startTime = System.currentTimeMillis();
        try (
                BufferedWriter output = new BufferedWriter(new FileWriter(outName))
        ) {
            //we need ISO mode?
            if (NCApplication.flags.isoMode) output.write("G291\n");

            for (String s : header) {
                //rename all tools in comments to T1/H1?
                if (NCApplication.flags.oneTool & s.matches(".*T\\d+.*")) {
                    s = s.replaceAll("T\\d+", "T1");
                }
                if (NCApplication.flags.oneTool & s.matches(".*H\\d+.*")) {
                    s = s.replaceAll("H\\d+", "H1");
                }
                output.write(s + "\n");
            }

            for (int i = 0; i < out.size(); i++) {
                if (NCApplication.flags.separateFiles) i = toolNumber;
                Tool tool = out.get(i);

                boolean coolantStarted = false;
                int toolRenamed = 0;

                for (String s : tool.data) {
                    //stop immedately if user press Stop
                    if (NCApplication.worker.isCancelled()) {
                        result += "Scan stopped\n";
                        return false;
                    }

                    //rename all tools to T1/H1?
                    if (NCApplication.flags.oneTool & toolRenamed < 2 & s.matches(".*T\\d+.*")) {
                        s = s.replaceAll("T\\d+", "T1");
                        toolRenamed++;
                    }
                    if (NCApplication.flags.oneTool & toolRenamed < 2 & s.matches(".*H\\d+.*")) {
                        s = s.replaceAll("H\\d+", "H1");
                        toolRenamed++;
                    }

                    //add M8 if we need coolant, but it not turn on in G-code
                    if (NCApplication.flags.coolant & !tool.coolant & !coolantStarted & s.matches(".*M0?3.*")) {
                        s = s + "\nM8";
                        coolantStarted = true;
                    }

                    //add M9 after spindle stop, if we have added coolant
                    if (NCApplication.flags.coolant & !tool.coolant & coolantStarted & s.matches(".*M0?5.*")) {
                        s = "M9\n" + s;
                    }

                    //add T1 in the end?
                    if (NCApplication.flags.toolAtEnd & s.contains("M30")) {
                        s = "T1 M6\n" + s;
                    }

                    output.write(s + "\n");
                }

                //Each tool = separate file,
                //if we have more than one tool
                if (NCApplication.flags.separateFiles & out.size() >= 2) {
                    //if not last tool, add M30 and T1?
                    if (toolNumber < out.size() - 1) {
                        if (NCApplication.flags.toolAtEnd) output.write("T1 M6\n");
                        output.write("M30\n\n");
                    }


                    //If current tool have subprogram calls,
                    //write only subprograms that we need
                    if(tool.bSubPrograms) {
                        for (String wantedSubName : tool.subProgramNames) {
                            for (Subprogram subProgram : sub) {
                                //if name matches,
                                //add this subprogram to output file
                                if (wantedSubName.equals(subProgram.name)) {
                                    for (String s : subProgram.data) output.write(s + "\n");
                                    break;
                                }
                            }
                        }
                    }
                    result += String.format("Saved output file for %d ms: %s\n", System.currentTimeMillis() - startTime, outName);
                    return true;
                }
            }

            //All tools in one file
            //write all subprograms
            for (Subprogram subProgram : sub) {
                for (String s : subProgram.data) output.write(s + "\n");
            }
        } catch (IOException e) {
            result += String.format("Error writing file: %s\n", outName);
            return false;
        }

        result += String.format("Saved output file for %d ms: %s\n", System.currentTimeMillis() - startTime, outName);
        return true;
    }

    //add line to the end of last active block
    private void add(String s) {
        switch (mode) {
            case HEADER -> {
                header.add(s);
                break;
            }

            case TOOL -> {
                out.get(out.size() - 1).data.add(s);
                break;
            }

            case SUBPROGRAM -> {
                sub.get(sub.size() - 1).data.add(s);
            }
        }
    }

    void rebuildLine() {
        StringBuilder sb = new StringBuilder();
        Stream.of(codes).forEach(p -> sb.append(p).append(" "));
        line = sb.toString().strip();
    }

    String getFirstCode() {
        if (codes.length == 0) return "";
        if (codes[0].startsWith("N")) return codes[1];
        else return codes[0];
    }

    String getSecondCode() {
        if (codes.length == 0) return "";
        if (codes[0].startsWith("N")) return codes[2];
        else return codes[1];
    }

    void addToFirstCode(String s) {
        if (s.isEmpty() | codes.length == 0) return;
        if (codes.length > 1 & codes[0].startsWith("N")) codes[1] = s + " " + codes[1];
        else codes[0] = s + " " + codes[0];
    }

    static class Tool {
        String name;
        boolean coolant;
        boolean bSubPrograms;
        ArrayList<String> data = new ArrayList<>();
        ArrayList<String> subProgramNames = new ArrayList<>();

        public Tool(String name) {
            this(name, false, false);
        }

        public Tool(String name, boolean coolant, boolean bSubPrograms) {
            this.name = name;
            this.coolant = coolant;
            this.bSubPrograms = bSubPrograms;
        }
    }

    enum Mode {
        HEADER,
        TOOL,
        SUBPROGRAM
    }

    static class Subprogram {
        String name;
        ArrayList<String> data = new ArrayList<>();

        public Subprogram(String name) {
            this.name = name;
        }
    }
}
