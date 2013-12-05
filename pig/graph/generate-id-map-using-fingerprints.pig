/*
 * Copyright 2013 Internet Archive
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You
 * may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License. 
 */

/* Input: Links with timestamp information
 * Output: A mapping of the link URLs/IDs to 64-bit fingerprint IDs (id.map)
 */

--links from the WATs and (optionally) from the crawl.log data
%default I_LINKS_DATA_DIR '/search/nara/congress112th/analysis/links-from-*/';
%default O_ID_MAP_DIR '/search/nara/congress112th/analysis/id.map';

REGISTER lib/ia-porky-jar-with-dependencies-CDH4.jar;
DEFINE FP org.archive.porky.Generate64BitFP();

Links = LOAD '$I_LINKS_DATA_DIR' as (src:chararray, timestamp:chararray, dst:chararray);
S = FOREACH Links GENERATE src as url;
D = FOREACH Links GENERATE dst as url;
A = UNION S, D;
A = DISTINCT A;

IdMap = FOREACH A GENERATE FP(url) as id, url;
Store IdMap into '$O_ID_MAP_DIR';
