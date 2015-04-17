#!/bin/bash

# require exactly one argument
if [ "$#" -ne 1 ]
then 
  echo "usage: `basename $0` <file>[.tex]"
  exit 1
fi

# take latexmlc and PERL5LIB from (possibly external) LATEXML_BASE 
: ${LATEXML_BASE:=/var/data/localmh/ext/LaTeXML}

# avoid to compute PERL5LIB from "which latexmlc"
export PERL5LIB=${LATEXML_BASE}/blib/lib
export STEXSTYDIR=/var/data/localmh/ext/sTeX/sty

inputfile=`readlink -f $1`

theory=`basename $inputfile .tex`

# ignore all.*tex files
if [ "$theory" == "all" -o "${theory:0:4}" == "all." ]
then 
  echo "ignoring argument: $1"
  exit 0
fi 

dir=`dirname $inputfile`

repoDir=`dirname $dir` # strip off final "source" path segment

repo=`basename $repoDir`
groupDir=`dirname $repoDir`
group=`basename $groupDir`
baseDir=`dirname $groupDir`

# if localpaths.tex exists, we take it as it is
if [ ! -f $dir/localpaths.tex ]
then
echo "creating $dir/localpaths.tex"
cat << EOF > $dir/localpaths.tex
% this file defines root path local repository
\defpath{MathHub}{$baseDir}
\mhcurrentrepos{$group/$repo}
\input{$repoDir/lib/WApersons}
% we also set the base URI for the LaTeXML transformation 
\baseURI[\MathHub{}]{https://mathhub.info/$group/$repo}
EOF
fi

cd $dir

exec ${LATEXML_BASE}/bin/latexmlc --profile smglom \
  --path=/var/data/localmh/sty $dir/${theory}.tex \
  --destination=$dir/${theory}.omdoc \
  --log=$dir/${theory}.ltxlog \
  --preamble=$repoDir/lib/pre.tex \
  --postamble=$repoDir/lib/post.tex