VALAM PDS: STAFF LOGINS AND MONTHLY SHOP LIMITS

This update contains the revised backend and website files. Copy the backend and frontend folders and Run-Website.bat into your existing ValamPDS\src folder, replacing those matching source files. Keep your existing database folder and its database file.

DEMO ACCOUNTS
Choose a work area first, then enter the matching account:

Warehouse
  User ID: warehouse.demo
  Password: ValamWH#2026

Fair Price Shop
  User ID: fps.demo
  Password: ValamFPS#2026

The two account types are checked separately. A warehouse account cannot sign in as a shop account, and a shop account cannot sign in as a warehouse account. Public account creation has been removed because staff accounts are meant to be issued by the district office.

Before signing in as FPS staff, select the assigned shop. Each shop has a separate demo user ID linked to its shop record: FPS01 uses fps.demo; another shop such as FPS07 uses fps.fps07. The demo password is ValamFPS#2026. The selected shop and FPS user ID must match. The session is then fixed to that shop until sign-out, and the shop cannot be changed from inside the website. New shops receive a matching demo user ID when added. Warehouse staff see warehouse stock, the full request queue, dispatch planning, and shop population/allocation records.

MONTHLY SHOP ALLOCATION DEMO ASSUMPTION
Each shop's planning allocation is estimated from its registered people count using project sample rates:
  Rice: 5 kg per person each month
  Wheat: 2 kg per person each month
  Sugar: 1 kg per person each month

Example: 100 registered people gives a shop-level planning estimate of 500 kg rice, 200 kg wheat, and 100 kg sugar. These figures are not a fixed entitlement or limit for each person or family. Family sizes differ, and actual household entitlements depend on applicable policy. The FPS dashboard shows confirmed goods received over the shop allocation for the month (for example, 500 / 1,700 kg). A dispatched delivery that has not been confirmed yet is shown as on the road and still reserves its amount, so repeated dispatches cannot exceed the limit. A request that exceeds the available amount stays pending with a reason. These rates are fictional sample settings for the project, not official policy. Configure them according to approved district rules before real use.

Shop population is entered when adding a shop and edited from its Edit details button. Warehouse dispatches are blocked if any requested commodity is above that shop's remaining monthly amount. A vehicle, route, and warehouse stock must also be available.

The demo credentials and rates are for this local academic project. They are not official government credentials, a government identity service, or actual policy rates. Passwords are stored in the local SQLite database as password hashes.

RUNNING THE WEBSITE
Open PowerShell in ValamPDS\src and run:
  .\Run-Website.bat
Then open http://localhost:8080 in a browser.

