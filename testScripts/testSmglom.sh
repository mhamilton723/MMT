#!/bin/bash

# run in MathHub/smglom
for i in *
do
    cd $i
    echo -e "log console\n log+ archive\n extension info.kwarc.mmt.stex.LaTeXML\n archive add .\n build smglom/$i latexml\n exit" | ../../../ext/MMT/mmt.jar
    cd ..
done
