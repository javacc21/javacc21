#!/usr/bin/env python3
# -*- coding: utf-8 -*-
#
# Copyright (C) 2021-2022 Vinay Sajip (vinay_sajip@yahoo.co.uk)
#
import argparse
import glob
import os
import re
import shutil
import subprocess
import sys
import tempfile
import time
from types import SimpleNamespace as Namespace

DEBUGGING = 'PY_DEBUG' in os.environ

JYTHON_PATH = None
VERSION_PATTERN = re.compile(r'\((\d+), (\d+), (\d+).*\)')

def check_jython(options):
    # First check for Java and Java compiler being available
    try:
        p = subprocess.run(['java', '-version'], capture_output=True)
    except Exception:
        print('Unable to locate Java interpreter.', file=sys.stderr)
        return 1
    try:
        p = subprocess.run(['javac', '-version'], capture_output=True)
    except Exception:
        print('Unable to locate Java compiler.', file=sys.stderr)
        return 1
    jd = options.jython_dir
    if not os.path.isdir(jd):
        print('Specified Jython directory '
              'doesn\'t exist: %s' % jd, file=sys.stderr)
        return 1
    global JYTHON_PATH
    JYTHON_PATH = os.path.join(jd, 'jython.jar')
    if not os.path.isfile(JYTHON_PATH):
        print('No jython.jar found in specified Jython directory: %s'
              % jd, file=sys.stderr)
        return 1
    p = subprocess.run(['java', '-jar', JYTHON_PATH, '-c',
                        'import sys; print(sys.version_info[:3])'],
                       capture_output=True)
    v = p.stdout.decode('utf-8').strip()
    m = VERSION_PATTERN.match(v)
    if not m:
        print('Unable to interpret Jython version: %s' % v,
              file=sys.stderr)
        return 1
    parts = tuple([int(n) for n in m.groups()])
    if parts < (2, 7, 2):
        print('Unsupported Jython version: %s' % v, file=sys.stderr)
        return 1
    return 0

def ensure_dir(p):
    d = os.path.dirname(p)
    if not os.path.exists(d):
        os.makedirs(d)

def copy_files(srcdir, destdir, patterns):
    for pattern in patterns:
        p = os.path.join(srcdir, pattern)
        for fn in glob.glob(p):
            rp = os.path.relpath(fn, srcdir)
            dp = os.path.join(destdir, rp)
            if os.path.isfile(fn):
                ensure_dir(dp)
                shutil.copy(fn, dp)
            else:
                shutil.copytree(fn, dp)
            # print('%s -> %s' % (p, dp))

def run_command(cmd, **kwargs):
    # print(' '.join(cmd))
    return subprocess.run(cmd, **kwargs)

def test_grammar(gdata, options):
    lang = gdata.dir  # Perhaps not intuitive, hence this comment
    s = 'Testing with %s grammar' % gdata.name
    line = '-' * 70
    print(line)
    print(s)
    print(line)

    # Create a temp directory and copy files into it.

    workdir = tempfile.mkdtemp(prefix='javacc-python-test-')
    gdata.workdir = workdir
    print('Working directory: %s' % workdir)
    if hasattr(gdata, 'srcdir'):
        sd = gdata.srcdir
    else:
        sd = os.path.join('examples', gdata.dir)
    dd = os.path.join(workdir, gdata.dir)
    copy_files(sd, dd, gdata.files)
    shutil.copy('ptest.py', dd)
    # Copy bitset speed up files if available and wanted
    if not options.no_speedup:
        d = os.path.expanduser('~/projects/cbitset')
        if os.path.isdir(d):
            copied = 0
            for spec in ('_bitset.py', '*.so'):
                files = glob.glob(os.path.join(d, spec))
                for f in files:
                    shutil.copy(f, dd)
                    copied += 1
            if copied:
                print('BitSet speedup files copied to working directory.')
    print('Test files copied to working directory.')

    # Run javacc to create the Java lexer and parser
    if lang == 'csharp':
        pf = os.path.join(dd, 'PPDirectiveLine.javacc')
        cmd = ['java', '-jar', 'javacc.jar', '-n', '-q', pf]
        p = run_command(cmd)
        if p.returncode:
            raise ValueError('Preprocessor generation in Java failed')
    gf = os.path.join(dd, gdata.grammar)
    cmd = ['java', '-jar', 'javacc.jar', '-n', '-q', gf]
    p = run_command(cmd)
    if p.returncode:
        raise ValueError('Parser generation in Java failed')
    print('Java version of lexer and parser created.')

    # Run javac to compile the Java files created. Assuming doing
    # the parser will sort everything else out

    jparser = gdata.jparser
    pkg, cls = jparser.rsplit('.', 1)
    fn = os.path.join(pkg.replace('.', os.sep), '%s.java' % cls)
    cmd = ['javac', fn]
    p = run_command(cmd, cwd=dd)
    if p.returncode:
        raise ValueError('Java compilation failed')
    print('Java lexer and parser compiled.')

    # Run Jython to create the Java test result files
    # For C#, you can't run the lexer standalone, because the parser switches lexical
    # states during e.g. string parsing

    if lang != 'csharp':
        # First the lexer
        cmd = ['java', '-jar', JYTHON_PATH, 'ptest.py', '-q',
               gdata.jlexer, gdata.ext]
        start = time.time()
        p = run_command(cmd, cwd=dd)
        if p.returncode:
            raise ValueError('Java lexer test run failed')
        elapsed = time.time() - start
        print('Java lexer run completed (%.2f secs).' % elapsed)

    # Then the parser

    cmd = ['java', '-jar', JYTHON_PATH, 'ptest.py', '-q',
           '--parser', gdata.production, gdata.jparser, gdata.ext]
    start = time.time()
    p = run_command(cmd, cwd=dd)
    if p.returncode:
        raise ValueError('Java parser test run failed')
    elapsed = time.time() - start
    print('Java parser run completed (%.2f secs).' % elapsed)

    # Run javacc to create the Python lexer and parser

    if lang == 'csharp':
        cmd = ['java', '-jar', 'javacc.jar', '-n', '-q', '-lang', 'python', pf]
        p = run_command(cmd)
        if p.returncode:
            raise ValueError('Preprocessor generation in Python failed')
    cmd = ['java', '-jar', 'javacc.jar', '-n', '-q', '-lang', 'python', gf]
    p = run_command(cmd)
    if p.returncode:
        raise ValueError('Parser generation in Python failed')
    print('Python version of lexer and parser created.')

    # Run Python to create the Python test result files
    # For C#, you can't run the lexer standalone, because the parser switches lexical
    # states during e.g. string parsing

    if lang != 'csharp':
        # First the lexer
        cmd = [sys.executable, 'ptest.py', '-q', gdata.ppackage, gdata.ext]
        start = time.time()
        p = run_command(cmd, cwd=dd)
        if p.returncode:
            raise ValueError('Python lexer test run failed')
        elapsed = time.time() - start
        print('Python lexer run completed (%.2f secs).' % elapsed)

    # Then the parser

    cmd = [sys.executable, 'ptest.py', '-q',
           '--parser', gdata.production, gdata.ppackage, gdata.ext]
    start = time.time()
    p = run_command(cmd, cwd=dd)
    if p.returncode:
        raise ValueError('Python parser test run failed')
    elapsed = time.time() - start
    print('Python parser run completed (%.2f secs).' % elapsed)

    # Compare differences between the directories

    cmd = ['diff', os.path.join('testfiles', 'results', 'java'),
           os.path.join('testfiles', 'results', 'python')]
    if os.name == 'nt':
        cmd.insert(1, '-b')
    p = run_command(cmd, cwd=dd)
    if p.returncode:
        raise ValueError('Test results differ - '
                         'should be identical')
    print('Results for Python & Java '
          'lexers & parsers are identical - yay!')

def main():
    # if sys.version_info[:2] < (3, 8):
        # print('Unsupported Python version %s: '
              # 'must be at least 3.8' % sys.version.split(' ', 1)[0])
        # return 1
    adhf = argparse.ArgumentDefaultsHelpFormatter
    ap = argparse.ArgumentParser(formatter_class=adhf)
    aa = ap.add_argument
    jd = os.environ.get('JYTHONDIR', os.path.expanduser('~/bin'))
    aa('--jython-dir', default=jd, help='Location of jython.jar')
    aa('--no-delete', default=False, action='store_true',
       help='Don\'t delete working directory')
    aa('--no-speedup', default=False, action='store_true',
       help='Don\'t use bitset speedup extension')
    aa('--langs', default='all', metavar='LANG1,LANG2...', help='Languages to test')
    options = ap.parse_args()
    # Check that jython is available
    check = 'JYTHONDIR' in os.environ
    if not check:
        global JYTHON_PATH
        JYTHON_PATH = os.path.expanduser('~/bin/jython.jar')
    else:
        rc = check_jython(options)
        if rc:
            return rc
    workdirs = []
    failed = False
    # This can all be read from a data source in due course
    languages = {
        'json': Namespace(name='JSON', dir='json',
                          grammar='JSON.javacc',
                          files=['JSON.javacc', 'testfiles'],
                          jlexer='org.parsers.json.JSONLexer',
                          jparser='org.parsers.json.JSONParser',
                          ppackage='jsonparser', ext='.json',
                          production='Root'),
        'java': Namespace(name='Java', dir='java',
                          grammar='Java.javacc',
                          files=['*.javacc', 'testfiles'],
                          jlexer='org.parsers.java.JavaLexer',
                          jparser='org.parsers.java.JavaParser',
                          ppackage='javaparser', ext='.java',
                          production='CompilationUnit'),
        'csharp': Namespace(name='CSharp', dir='csharp',
                            grammar='CSharp.javacc',
                            files=['*.javacc', 'testfiles'],
                            jlexer='org.parsers.csharp.CSharpLexer',
                            jparser='org.parsers.csharp.CSharpParser',
                            ppackage='csharpparser', ext='.cs',
                            production='CompilationUnit'),
        'python': Namespace(name='Python', dir='python',
                            grammar='Python.javacc',
                            files=['*.javacc', 'testfiles'],
                            jlexer='org.parsers.python.PythonLexer',
                            jparser='org.parsers.python.PythonParser',
                            ppackage='pythonparser', ext='.py',
                            production='Module'),
    }
    try:
        langs = options.langs.split(',')
        for lang, gdata in languages.items():
            if options.langs == 'all' or lang in langs:
                test_grammar(gdata, options)
                workdirs.append(gdata.workdir)

    except Exception as e:
        print('Failed: %s.' % e)
        failed = True
        return 1
    finally:
        if failed or options.no_delete:
            for workdir in workdirs:
                print('TODO rm -rf %s' % workdir)
        else:
            for workdir in workdirs:
                shutil.rmtree(workdir)
            print('Working directories deleted.')


if __name__ == '__main__':
    try:
        rc = main()
    except KeyboardInterrupt:
        rc = 2
    except Exception as e:
        if DEBUGGING:
            s = ' %s:' % type(e).__name__
        else:
            s = ''
        sys.stderr.write('Failed:%s %s\n' % (s, e))
        if DEBUGGING: import traceback; traceback.print_exc()
        rc = 1
    sys.exit(rc)
