#!/bin/sh
cd "$(dirname "$0")"
java -jar ExpenseInNutshell.jar "$@"
status=$?
if [ $status -ne 0 ]; then
  echo ""
  echo "Something went wrong. See the message above."
  read -r -p "Press Enter to close..." _
fi
exit $status
