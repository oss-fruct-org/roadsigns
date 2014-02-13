SRC=$1
DEST=$2

BASENAME=$(basename ${SRC})
NAME="${BASENAME%.*}".png

DEST_M=${DEST}/drawable-mdpi
DEST_H=${DEST}/drawable-hdpi
DEST_XH=${DEST}/drawable-xhdpi
DEST_XXH=${DEST}/drawable-xxhdpi

mkdir ${DEST_M} -p
mkdir ${DEST_H} -p
mkdir ${DEST_XH} -p
mkdir ${DEST_XXH} -p

inkscape -z -e ${DEST_M}/${NAME} -w 32 -h 32 ${SRC}
inkscape -z -e ${DEST_H}/${NAME} -w 48 -h 48 ${SRC}
inkscape -z -e ${DEST_XH}/${NAME} -w 64 -h 64 ${SRC}
inkscape -z -e ${DEST_XXH}/${NAME} -w 96 -h 96 ${SRC}

