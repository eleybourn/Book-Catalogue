Archive Format
==============

Assumed to have multiple 'entries', processed sequentially. Could be zip, tar, or any other 
amenable stream

First entry: INFO.xml
---------------------

Contains a simple name-value set including:

ArchiverVersion
   Archiver internal Version number (initially 1)
CreateDate
   Creation Date of archive (in SQL format)
NumBooks
   # of books in archive (will/may be fewer pictures)
AppVersion
   BC Version that created it
CompatArchiver
   Last known compatible archiver version (initially 1, ideally always 1) that can read this archive
   We may in th dim distance future decide to make a new TAR format that older versions will be unable to
   understand. This is only a relevant field for DOWNGRADING.

Optional Entries: INFO_*.xml
----------------------------

Later versions of the archiver *may* introduce specific INFO files for their versions. 
The archiver should skip over INFO files it does not understand or expect.

First Data Entry: BOOKS.CSV
---------------------------

A CSV export appropriate for the archiver that created the archive. ie. the most recent 
archiver version for the app version that is installed.

Optional Data Entries: BOOKS_<ArchVersion>.CSV
----------------------------------------------

For backwards compatibility, there may be alternate CSV files for older versions. The ArchVersion field
indicates the last version it was completely compatible with.

Scanning for BOOKS*.csv
-----------------------

The basic rule is to scan the file until the last BOOKS*.csv is found and use the version that
has an ArchVersion >= the current archiver version, or BOOKS.CSV if none match. eg.

Suppose the archive contains:

BOOKS.csv
BOOKS_3.csv
BOOKS_5.csv

if the current Archiver is version 7 then use BOOKS.csv
if the current Archiver is version 5 then use BOOKS_5.csv
if the current Archiver is version 4 then use BOOKS_5.csv
if the current Archiver is version 1 then use BOOKS_3.csv
 
Remaining Data Entries: *.*
---------------------------

"Snapshot.db" 
	Optional. A copy of the database.

Any other name is assumed to be a cover image.

 