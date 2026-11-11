Valam PDS website UI and interaction fixes

Copy the frontend folder and backend\WebServer.java into C:\ValamPDS\src, replacing the matching files. Keep your existing database and other project files. Restart Run-Website.bat after copying.

Changes:
- Added CSV download to FPS receipt history.
- Moved request creation to FPS accounts; warehouse accounts can still view and process pending requests, and the backend rejects warehouse request creation.
- Removed inactive notification, board filter, and misleading card actions.
- Replaced the hard-coded “Today” card label with the actual request/delivery status.
- Removed the local academic demonstration disclaimer and simplified the login footer.
- Search now reports when no matching section is found.

This is an overlay for the existing project, not a replacement database or full project archive.
