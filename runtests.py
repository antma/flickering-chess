#!/usr/bin/python3

import environment_vars
import os, os.path, glob, sys

BUILD_DIR="build"
RUNNER='org.junit.runner.JUnitCore'
ev = environment_vars

lst_classpath = [os.path.join(ev.KOTLIN_LIB, 'kotlin-stdlib.jar'), os.path.join(ev.KOTLIN_LIB, 'kotlin-test.jar'), ev.JUNIT4_JAR,  ev.HAMCREST_CORE_JAR, \
os.path.join(BUILD_DIR, 'flickering-chess.jar'), os.path.join(BUILD_DIR, 'flickering-chess-test.jar')]
classpath = ':'.join(lst_classpath)
tests = []
for s in glob.glob(os.path.join('src', 'test', 'kotlin', '*.kt')):
  name, ext = os.path.splitext(os.path.basename(s))
  tests.append(name)

if os.system('ant test-compile') != 0:
  sys.stderr.write('Ant failed\n')
  sys.exit(1)

cmd = 'java -cp ' + classpath + ' ' + RUNNER + ' ' + ' '.join(tests)
print(cmd)
os.system(cmd)
