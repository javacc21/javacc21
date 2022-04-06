#!/usr/bin/env python3
# -*- coding: utf-8 -*-
#
# Copyright (C) 2021-2022 Vinay Sajip (vinay_sajip@yahoo.co.uk)
#
from __future__ import print_function    # needed forIronPython
import argparse
import importlib
import logging
import os
import sys

DEBUGGING = 'PY_DEBUG' in os.environ

IS_JAVA = sys.platform.startswith('java')
IS_DOTNET = sys.platform == 'cli'

logger = logging.getLogger(__name__)

if IS_JAVA:
    import java.io
    from java.nio.charset import StandardCharsets

    def node_repr(node):
        if isinstance(node, Token):
            return node.image
        cn = type(node).simpleName
        return '<%s (%s, %s)-(%s, %s)>' % (cn, node.beginLine,
                                           node.beginColumn, node.endLine,
                                           node.endColumn)

    def java_dump_node(stream, node, level=0):
        indstr = '  ' * level
        s = '%s%s\n' % (indstr, node_repr(node))
        stream.print(s)
        for child in node.children():
            java_dump_node(stream, child, level + 1)

    def get_reader(p):
        fis = java.io.FileInputStream(p)
        isr = java.io.InputStreamReader(fis, StandardCharsets.UTF_8)
        return java.io.BufferedReader(isr)

    def get_writer(p):
        fos = java.io.FileOutputStream(p)
        osw = java.io.BufferedWriter(java.io.OutputStreamWriter(fos, StandardCharsets.UTF_8))
        return java.io.PrintWriter(osw, True)

elif IS_DOTNET:
    try:
        sd = [d for d in os.listdir('.') if d.startswith('cs-') and os.path.isdir(d)]
        if not sd:
            raise ValueError('No subdirectory starting with cs- found.')
        sd = os.path.join(sd[0], 'bin', 'Debug', 'netstandard2.1')
        if not os.path.isdir(sd):
            raise ValueError('Not a directory: %s' % sd)
        sd = os.path.abspath(sd)
        # print('Adding to sys.path: %s' % sd)
        sys.path.append(sd)
        import clr
        dlls = [f for f in os.listdir(sd) if f.endswith('.dll')]
        if not dlls:
            raise ValueError('No .dll found in %s' % sd)
        for dll in dlls:
            fn = os.path.join(sd, dll)
            # print('Adding reference to %s (%s)' % (fn, dll))
            clr.AddReferenceToFile(dll)
        # print('DLLs added.')
        from System.IO import FileStream, StreamWriter, FileMode

        def clr_import_module(name):  # Because IronPython's import_module appears to be broken
            parts = name.split('.')
            mod = None
            try:
                for p in parts:
                    if mod is None:
                        __import__(p)
                        mod = sys.modules[p]
                    else:
                        mod = getattr(mod, p)
            except Exception as e:
                msg = 'Failed to import %s at part %s' % (name, p)
                raise ImportError(msg)
            sys.modules[name] = mod
            return mod

        def node_repr(node):
            if isinstance(node, Token):
                return node.Image
            cn = type(node).__name__
            return '<%s (%s, %s)-(%s, %s)>' % (cn, node.BeginLine,
                                               node.BeginColumn, node.EndLine,
                                               node.EndColumn)

        def csharp_dump_node(stream, node, level=0):
            indstr = '  ' * level
            s = '%s%s\n' % (indstr, node_repr(node))
            stream.Write(s)
            if node.Children:
                for child in node.Children:
                    csharp_dump_node(stream, child, level + 1)
    except Exception as e:
        import traceback; traceback.print_exc()
        raise

else:
    def python_dump_node(stream, node, level=0):
        indstr = '  ' * level
        s = '%s%s\n' % (indstr, node)
        stream.write(s)
        for child in node.children:
            python_dump_node(stream, child, level + 1)


def main():
    fn = os.path.expanduser('~/logs/ptest.log')
    logging.basicConfig(level=logging.DEBUG, filename=fn, filemode='w',
                        format='%(message)s')
    adhf = argparse.ArgumentDefaultsHelpFormatter
    ap = argparse.ArgumentParser(formatter_class=adhf)
    aa = ap.add_argument
    aa('package', metavar='PACKAGE', help='Package for parser/lexer (specify fully qualified parser/lexer class name for Java)')
    aa('ext', metavar='EXT', help='File extension to process (all others are ignored)')
    aa('--parser', default=None, metavar='PRODUCTION', help='Test parser with specified production (otherwise, lexer is tested)')
    aa('--source', default='testfiles', help='Source directory to process')
    aa('--results', default=os.path.join('testfiles', 'results'), help='Directory to write results to')
    aa('-q', '--quiet', default=False, action='store_true', help='Minimise output verbosity')
    aa('-m', '--match', metavar='SUBSTRING', help='Only process files which contain the specified substring')
    aa('-x', '--exclude', metavar='SUBSTRING', help='Only process files which don\'t contain the specified substring')
    options = ap.parse_args()
    ext = options.ext
    if not ext.startswith('.'):
        ext = '.%s' % ext
    # print('Running under %s' % sys.version.replace('\n', ' '))
    global Token
    if IS_JAVA:
        resdir = 'java'
        pkg, cls = options.package.rsplit('.', 1)
        mod = importlib.import_module(pkg)
        if options.parser:
            Parser = getattr(mod, cls)
            ParseException = getattr(mod, 'ParseException')
            Token = getattr(mod, 'Token')
        else:
            Lexer = getattr(mod, cls)
            TokenType = getattr(Lexer, 'TokenType')
    elif IS_DOTNET:
        resdir = 'csharp'
        # import pdb; pdb.set_trace()
        mod = clr_import_module(options.package)
        if options.parser:
            Parser = getattr(mod, 'Parser')
            ParseException = getattr(mod, 'ParseException')
        else:
            Lexer = getattr(mod, 'Lexer')
            TokenType = getattr(mod, 'TokenType')
        Token = getattr(mod, 'Token')
    else:
        resdir = 'python'
        cwd = os.getcwd()
        if cwd not in sys.path:
            sys.path.insert(0, cwd)
        mod = importlib.import_module(options.package)
        if options.parser:
            Parser = getattr(mod, 'Parser')
            ParseException = getattr(mod, 'ParseException')
        else:
            Lexer = getattr(mod, 'Lexer')
            TokenType = getattr(mod, 'TokenType')
    outext = '.ast.txt' if options.parser else '.pos.txt'

    for fn in os.listdir(options.source):
        if fn.endswith(ext):
            if options.match and options.match not in fn:
                continue
            if options.exclude and options.exclude in fn:
                continue
            p = os.path.join(options.source, fn)
            od = os.path.join(options.results, resdir)
            if not os.path.exists(od):
                os.makedirs(od)
            ofn = os.path.join(od, fn.replace(ext, outext))
            if not options.quiet:
                print('Processing %s -> %s' % (fn, os.path.basename(ofn)))
                logger.debug('Processing %s -> %s', fn, os.path.basename(ofn))
            try:
                if IS_JAVA:
                    f = get_reader(p)
                    outf = get_writer(ofn)
                    if options.parser:
                        parser = Parser(f)
                        parser.inputSource = p
                    else:
                        lexer = Lexer(f)
                        lexer.inputSource = p
                elif IS_DOTNET:
                    f = None
                    outf = StreamWriter(FileStream(ofn, FileMode.Create))
                    # import pdb; pdb.set_trace()
                    if options.parser:
                        parser = Parser(p)
                    else:
                        lexer = Lexer(p)
                else:
                    f = None
                    outf = open(ofn, 'w', encoding='utf-8')
                    if options.parser:
                        parser = Parser(p)
                    else:
                        lexer = Lexer(p)
                if options.parser:
                    try:
                        if IS_JAVA:
                            # import pdb; pdb.set_trace()
                            getattr(parser, options.parser)()
                            node = parser.rootNode()
                            java_dump_node(outf, node, 0)
                        elif IS_DOTNET:
                            getattr(parser, 'Parse%s' % options.parser)()
                            node = parser.RootNode
                            csharp_dump_node(outf, node, 0)
                        else:
                            # import pdb; pdb.set_trace()
                            getattr(parser, 'parse_%s' % options.parser)()
                            node = parser.root_node
                            python_dump_node(outf, node, 0)
                    except ParseException as e:
                        print('Parse failed: %s' % e)
                        logger.exception('Parse failed: %s', e)
                        if 'invalid.json' not in p:
                            raise
                else:
                    done = False
                    while not done:
                        if IS_JAVA:
                            t = lexer.getNextToken(None)
                            s = '%s: %s %d %d %d %d\n' % (t.type, t.image,
                                                          t.beginLine,
                                                          t.beginColumn,
                                                          t.endLine,
                                                          t.endColumn)
                            outf.print(s)
                        elif IS_DOTNET:
                            t = lexer.GetNextToken(None)
                            s = '%s: %s %d %d %d %d\n' % (t.Type, t.Image,
                                                          t.BeginLine,
                                                          t.BeginColumn,
                                                          t.EndLine,
                                                          t.EndColumn)
                            outf.Write(s)
                        else:
                            # import pdb; pdb.set_trace()
                            t = lexer.get_next_token(None)
                            s = '%s: %s %d %d %d %d\n' % (t.type.name, t.image,
                                                          t.begin_line,
                                                          t.begin_column,
                                                          t.end_line,
                                                          t.end_column)
                            outf.write(s)
                        if IS_DOTNET:
                            # import pdb; pdb.set_trace()
                            done = t.Type == TokenType.EOF
                        else:
                            done = t.type == TokenType.EOF
            finally:
                if f:
                    f.close()
                if outf:
                    if IS_DOTNET:
                        outf.Close()
                    else:
                        outf.close()

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
