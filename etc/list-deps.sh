./gradlew termx-app:dependencies \
  | sed -n "s/.*\+\-\- \(.*\)/\1/p" \
  | grep -v "^project" \
  | grep -v "(n)" \
  | sed 's/\(:.*\)\? \-> /:/g' \
  | sed 's/ ([\*c])$//g' \
  | sed  's/$/  /g' \
  | sort \
  | uniq
