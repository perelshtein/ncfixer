NcFixer v1.0
====================================================

This program help to prepare NC files for several CNC milling machines.
It process text files (.nc) with G-codes and eleminate manual text editing.

I use it with MasterCam X9, with option Fanuc3x-mill.
I prepare nc-files for 3-axis milling machines:
1) Stankomach FS65MF3 (Siemens 828D);
2) Sk-Router 6040V3KM (LinuxCNC).

You can:
1) Remove % and O0000 in header;
2) Remove comments in header;
3) On/off ISO mode for Siemens (G291);
4) Remove all lines with G28 commands (because Siemens does not recognize G28 Z-axis coordinates);
5) Enable fast move (G00) after end of drill cycles (G81-G89). This option useful for Linux CNC;
6) Enable coolant check. It checks that coolant is enabled for each tool and add M08/M09 command, if not;
7) Enable retract check. It checks max drill retract (R) limit in cycles G81-G89. You may set up it to 1.5, for example. If you forget change retract in one of the cycles and leave it to R20 (greater than 1.5), it will be changed to R1.5;
8) Remove line numbers (N12345..) at start of line;
9) Rename all tools to T1. If you already have program with several tools and want to run it ot older machine with one tool, use this option.
10) One tool - one file. You may split the big program and save every tool to separate file. Files will be named ust1-01.nc, ust1-02.nc, and so on.
11) Call T1 tool at end of program. If you use T1 as referent tool, it may be useful.

You may also select output extension to save processed file.
You can process one file or folder. If folder selected, then there are an option to take only files with specified extension (by default, .nc).

====================================================

You may also download test files (in "test" folder), to see how it works.

I run this program on Linux (Ubuntu) with Java 16 (openjdk-16) and Windows 10 with Java 17 (Amazon coretto).
With Java 8, it will NOT work.
For Linux, you can download jar file, mark it as executable and run. It need Java installed.
For Windows, you can download exe file. If you has not yet Java installed, download page will be opened.

====================================================

If you have any ideas to improve, or found a bug, please let me know:
http://www.github.com/perelshtein


