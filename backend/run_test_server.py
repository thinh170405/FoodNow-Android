"""Isolated emulator/browser integration fixture. Never use as the demo server."""
from pathlib import Path
import tempfile
import server
import argparse

if __name__ == '__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--port',type=int,default=8081);args=parser.parse_args()
    with tempfile.TemporaryDirectory(prefix='foodnow-test-') as directory:
        server.initialize(Path(directory) / 'test.db', bootstrap=False)
        with server.connection() as conn:
            conn.execute("INSERT INTO users(email,name,password_hash,role) VALUES(?,?,?,'admin')",
                         ('admin@test.local', 'Integration Admin', server.password_hash('Admin12345')))
        print(f'Isolated test server on {args.port}', flush=True)
        server.ThreadingHTTPServer(('127.0.0.1', args.port), server.Handler).serve_forever()
