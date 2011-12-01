The purpose of this project is to create a comprehensive database of similar
names to include in searches whenever a particular name is searched.
It is a better name-matcher than Soundex.  Read more about the
[Similar names project](http://www.werelate.org/wiki/WeRelate:Similar_names_project)

This readme explains how to incorporate similar names into your own website.

Search module
=============

The search module contains everything you need to incorporate similar names into
your own website.

* _Normalizer.java_ - converts a user-entered text string to a normalized form -
converts non-roman characters to corresponding roman characters, removes noise
words, prepends surname prefixes, converts -dotter endings to -son, etc.
You must call this on user-entered text before calling any other function.
See _NormalizerTest.java_ for an example.

* _Searcher.java_ - the two main functions are _getAdditionalIndexTokens_ and
_getAdditionalSearchTokens_. See _SearcherTest.java_ for an example.

    * Each name should be indexed under its normalized form as well as any tokens
returned by _getAdditionalIndexTokens_. _getAdditionalIndexTokens_ returns the
soundex code for the name when the name rare. It does not return any tokens
otherwise.  Thus, most names require just a single token (the normalized name
itself), while some names require two tokens.

    * To return similar names for a user-specified name at search time, search for
the normalized form of the user-specified name as well as additional tokens
returned by _getAdditionalSearchTokens_. On average, _getAdditionalSearchTokens_
will return 25-35 tokens to include in searches.

* _surnamePrefixedNames.txt_ contains a list of prefixed-surnames (e.g., McDonald),
and their unprefixed roots (e.g., Donald).  According to the labeled pairs
provided by Ancestry, unprefixed roots need to be included in searches for a
prefixed surname, and vice-versa. But determining whether a name is prefixed is
not easy.  That is, Vandyke and Ohare are prefixed, but Vance and Olson are not.
This table attempts to identify common prefixed surnames.  Rare surnames use
the prefix list in _searcher.properties_ to estimate whether they are prefixed.

* _givenname\_similar\_names.csv_ is a table of similar names for the 70,000
most-frequent given names in Ancestry.com's database.

* _surname\_similar\_names.csv_ is a table of similar names for the 200,000
most-frequent surnames in Ancestry.com's database.

Rare names not in the above tables appear less than once in every 5,000,000
names in Ancestry's database, are indexed under their Soundex code by
_getAdditionalIndexTokens_.  _getAdditionalSearchTokens_ includes these names by
including the Soundex code of the searched-for name as one of the tokens to search.

The above tables will be updated periodically to incorporate user modifications
made at the [Similar names project](http://www.werelate.org/wiki/WeRelate:Similar_names_project).
As part of this project, people are being encouraged not only to improve the names,
but also to review the changes made by others.  A changes log
(see [Changes log](http://www.werelate.org/wiki/Special:NamesLog)) will be included
here so you can browse the changes.

_Searcher.java_ reads the two above tables to determine whether a name must be
indexed under a Soundex token (if it is not in the table), and what additional
names to include in searches.  If you don't want to use java, you could pretty
easily write your own code to read the two tables.

Installing the tables into a database
-------------------------------------

By default, _Searcher.java_ reads the above two tables into memory.  If you want to
read the tables from a database instead of reading them into memory, do the following:

    create table givenname_similar_names (
    name varchar(255) not null,
    similar_names varchar(4096) not null,
    primary key (name));

    mysqlimport --fields-enclosed-by='"' --fields-terminated-by=','
    --lines-terminated-by="\\n" --local <dbname> givenname_similar_names.csv

    create table surname_similar_names (
    name varchar(255) not null,
    similar_names varchar(4096) not null,
    primary key (name));

    mysqlimport --fields-enclosed-by='"' --fields-terminated-by=','
    --lines-terminated-by="\\n" --local <dbname> surname_similar_names.csv

In addition,

* copy c3p0.properties.example to c3p0.properties, customize it as needed, and make sure it is on your classpath

* copy db\_memcache.properties.example to db\_memcache.properties, customize it as needed, and make sure it is on your classpth

Building
--------

`mvn install` &nbsp; creates the normal jar file as well as one with all dependencies


Score module
============

The score module contains code to _score_ a pair of names: return a number
indicating how similar they are.

* _Scorer.java_ - contains the scoring function.  See _ScorerTest.java_ for an
example of use.  Remember to normalize the names beforehand.

* _SimilarNameGenerator.java_ returns a set of names similar to the specified
name. The core of the similar-names tables in the search module were built
using this function.  See _SimilarNameGeneratorTest.java_ for an example.

* _SimilarNameAugmenter.java_ adds additional names to a similar-names file.
If you had your own custom list of similar names, they could be added to the
similar-names tables in the search module using this function.  '''If you do
have your own list of similar names, we hope you share them with us.'''

* _DMSoundex.java_ - a simplified implementation of Daitch-Mokotov soundex

* _Nysiis.java_ - an implementation of NYSIIS

* _WeightedEditDistance.java_ - an edit distance algorithm, like Levenstein,
but where each edit is assigned a weight, where the weights have been learned
using an Expectation Maximization algorithm over the positive labled examples
provided by Ancestry.  By weighting edits, we can make the edit distance between
ACE and APE greater than the edit distance between ACE and ASE.

* _FeaturesGenerator.java_ - when name pairs are scored, a set of features is
generated for each pair.  These features include whether the NYSIIS codes match,
the Soundex codes match, the Refined Soundex codes match, the Daitch-Mokotov
codes match, the value of the Levenstein distance, and the value of the Weighted
Edit Distance.  The weights for each feature were learned by running the labeled
training data provided by Ancestry.com through [Weka](http://www.cs.waikato.ac.nz/ml/weka/)
A number of other features were evaluated, but these features seemed to provide
the best results.

* _givennameClusters.txt_ and _surnameClusters.txt_ - these files can be used
to speed up _SimilarNameGenerator_.  By clustering the names, rather than scoring
each name against the specified name to determine if it is similar, we can score
the cluster "centroids", and score the names in a cluster only if the centroid is
closer than a certain threshold.

* _cmulex\_lts.bin_ - this file was created by people at Carnegie Mellon University,
and is used by the FreeTTS library to convert name strings to phonetic values. The
phonetic values are used in WeightedEditDistance - it calculates the distance
between the phonemes.

You might be tempted to index each non-rare name under its cluster centroid,
so that you'd only have one token to look up at search time. You could do this, and
you'll get reasonable results. But you'll get better results using the approach
described in the search module.  The reason is that while name X might be similar
to name Y, and name Y might be similar to name Z, name X may not be very similar to
name Z.  In other words, similarity is reflexive but not transitive. Attempting to
cluster names and searching on cluster centroids only, assumes transitivity, which
doesn't exist.

Installation and Building
-------------------------

Before using _Scorer.java_ you must install the customized freetts.jar provided in
the _external_ directory into your local maven repository:

    mvn install:install-file -Dfile=external/freetts.jar -DgroupId=com.sun.speech
    -DartifactId=freetts -Dversion=1.2.2-threadsafe -Dpackaging=jar

`mvn install` &nbsp; creates the normal jar file as well as one with all dependencies

Eval module
===========

Use this module if you want to evaluate the precision and recall of your own coder or
similar-names tables against Ancestry's labeled pairs.

* _CodeEvaluator.java_ shows how to evaluate a code-based approach; modify this to
incorporate your coder.

* _TableEvaluator.java_ shows how to evaluate a table-based approach.

* _AncestryGivennamePairs.csv_, _AncestrySurnamePairs.csv_, and _BorderSurnamePairs.csv_
contain the labeled pairs provided by Ancestry.  However, BorderSurnamePairs was found
to not help, so it was not used.

* _givenname\_ancestry.txt_ and _surname\_ancestry.txt_ contain the positively-labeled
pairs from Ancestry's labeled data.

* _givenname\_nicknames.txt_ contains a set of nicknames.

* _givenname\_werelate.txt_ and _surname\_werelate.txt_ contain name pairs given by
users at WeRelate.org, the book "Dunkling, Leslie and William Gosling, _The New
American Dictionary of Baby Names_, published by Signet (New American Library), 1985",
and the book "Patrick Hanks and Flavia Hodges, _A Dictionary of Surnames_, Oxford
University Press, 1990."

* _givenname\_similar\_names\_orig.csv_ and _surname\_similar\_names\_orig.csv_ contain
the original core of the similar names tables.

To create the similar names tables in the search module, the SimilarNameGenerator from
the Score module was used to create _givenname\_similar\_names\_orig.csv_ and
_surname\_similar\_names\_orig.csv_.  _givenname\_nicknames.txt_ and
_givenname\_werelate.txt_ were added to the given names table, and
_surname\_werelate.txt_ was added to the surnames table, using SimilarNameAugmenter
from the Score module. At this point, the Precision and Recall of the similar-names
tables against Ancestry's labeled pairs were:

* Given names: P/R=96.9/73.7
* Surnames: P/R=89.2/76.8

Finally, we added _givenname\_ancestry.txt_ and _surname\_ancestry.txt_ to create the
tables found in the Search module, although user-modifications to these tables as part of
the [Similar names project](http://www.werelate.org/wiki/WeRelate:Similar_names_project)
will further modify, and hopefully improve, these tables.