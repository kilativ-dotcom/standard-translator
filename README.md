## standard-translator

This is a program that translates ostis-standard scnheaders.

To set translation pairs place csv files with two columns(Russian and English) into `translations` folder.

To run you can use script
```shell
./scripts/start.sh /absolute/path/to/folder/with/tex/files
```

In case same scnheader is met multiple times only first occurrence will be translated and program will warn about duplicates after all files are translated.

Not used translations will be placed in `translations/dictionary.tex` in pairs like

```tex
\scnheader{машина логического вывода с учетом фактора времени}
\scnidtf{machine of logical inference with respect to time}

\scnheader{правило интерпретации*}
\scnidtf{interpretation rule*}

\scnheader{scp-метапрограмма}
\scnidtf{scp-metaprogram}
```

### **_If you want to run this program you will need Java8 installed_**