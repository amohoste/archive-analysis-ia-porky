/*
 * Copyright 2014 Internet Archive
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

package org.archive.porky;
import java.io.IOException;
import java.util.Properties;
import org.apache.pig.EvalFunc;
import org.apache.pig.data.Tuple;
import org.apache.pig.impl.util.WrappedIOException;
import java.util.regex.*;
import java.io.*;
import java.net.*;
import org.apache.pig.PigException;
import org.apache.pig.backend.executionengine.ExecException;
import org.apache.pig.data.TupleFactory;
import org.apache.pig.data.DataBag;
import org.apache.pig.data.DataType;
import org.apache.pig.data.Tuple;
import org.apache.pig.builtin.MonitoredUDF;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;
import java.util.EnumMap;
import edu.stanford.nlp.ie.AbstractSequenceClassifier;
import edu.stanford.nlp.ie.crf.*;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.Integer;
import java.util.concurrent.TimeUnit;

/**
 * UDF which reads in a text string, and returns entities identified by the configured Stanford NER classifier
 * @author vinay
 */ 

//@MonitoredUDF(timeUnit = TimeUnit.MILLISECONDS, duration = 120000, stringDefault = "{PERSON=[], ORGANIZATION=[], LOCATION=[]}")
public class NER3ClassUDF extends EvalFunc<String> {
	
  String serializedClassifier;
  CRFClassifier classifier = null;

  public NER3ClassUDF(String file) {
  	serializedClassifier = file;
  }

  public enum NERClassType { PERSON, ORGANIZATION, LOCATION, O }
  
  public String exec(Tuple input) throws IOException {

	String emptyString = "{PERSON=[], ORGANIZATION=[], LOCATION=[]}";
	Map<NERClassType, Set<String>> entitiesByType = new EnumMap<NERClassType, Set<String>>(NERClassType.class);
	for (NERClassType t : NERClassType.values()) {
		if(t != NERClassType.O)
			entitiesByType.put(t, new HashSet<String>());
	}

	NERClassType prevEntityType = NERClassType.O;
	String entityBuffer = "";

	if(input == null || input.size() == 0) {
		return emptyString;
	}

	try {
		String textString = (String)input.get(0);
		if(textString == null) {
			return emptyString;
		}
		
		if(classifier == null) {
			//initialize
			Properties props = new Properties();
			props.put("tokenizerOptions", "untokenizable=noneDelete");
			classifier = CRFClassifier.getClassifier(serializedClassifier, props);
		}
		
		List<List<CoreLabel>> out = classifier.classify(textString);
		for (List<CoreLabel> sentence : out) {
			for (CoreLabel word : sentence) {
				String wordText = word.word();
				String classText = word.get(CoreAnnotations.AnswerAnnotation.class);
				NERClassType currEntityType = NERClassType.valueOf(classText);
				if (prevEntityType != currEntityType) {
					if(prevEntityType != NERClassType.O && !entityBuffer.equals("")) {
						//time to commit
						entitiesByType.get(prevEntityType).add(entityBuffer);
						entityBuffer = "";
					}
				}
				prevEntityType = currEntityType;
				if(currEntityType != NERClassType.O) {
					if(entityBuffer.equals(""))
						entityBuffer = wordText;
					else
						entityBuffer+= " " + wordText;
				} 
			}
			//end of sentence
			//apply commit and reset
			if(prevEntityType != NERClassType.O && !entityBuffer.equals("")) {
				entitiesByType.get(prevEntityType).add(entityBuffer);
				entityBuffer = "";
			}
			//reset
			prevEntityType = NERClassType.O;
			entityBuffer = "";
		}
		return entitiesByType.toString();
	
	} catch(Exception e) { 
		if(classifier == null)
			throw WrappedIOException.wrap("Unable to load classifier ", e);
                return emptyString;
        }
  }
}
