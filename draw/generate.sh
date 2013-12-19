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

inkscape -z -e ${DEST_M}/${NAME} -d 90 ${SRC}
inkscape -z -e ${DEST_H}/${NAME} -d 135 ${SRC}
inkscape -z -e ${DEST_XH}/${NAME} -d 180 ${SRC}
inkscape -z -e ${DEST_XXH}/${NAME} -d 270 ${SRC}

