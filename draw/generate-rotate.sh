SRC=$1

function rotate() {
    BASENAME=$(basename ${1})
    NAME="${BASENAME%.*}"
    NAMER=${NAME}_${2}.svg

    cp ${SRC} /tmp/${NAMER}
    inkscape /tmp/${NAMER} --select=arrow --verb=${3} --verb=FileSave --verb=FileClose
    ./generate.sh /tmp/${NAMER} out
}

rotate ${SRC} 90 ObjectRotate90
rotate ${SRC} 270 ObjectRotate90CCW
rotate ${SRC} 180 ObjectFlipVertically
./generate.sh ${SRC} out
