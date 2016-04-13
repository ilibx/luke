package org.apache.lucene.luke.ui;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.*;
import org.apache.lucene.index.TermsEnum.SeekStatus;
import org.apache.lucene.luke.core.IndexInfo;
import org.apache.lucene.luke.core.Util;
import org.apache.lucene.luke.core.decoders.Decoder;
import org.apache.lucene.luke.ui.LukeWindow.LukeMediator;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.TFIDFSimilarity;
import org.apache.lucene.util.*;
import org.apache.pivot.beans.BXML;
import org.apache.pivot.beans.Bindable;
import org.apache.pivot.collections.*;
import org.apache.pivot.collections.ArrayList;
import org.apache.pivot.collections.HashMap;
import org.apache.pivot.collections.List;
import org.apache.pivot.collections.Map;
import org.apache.pivot.util.Resources;
import org.apache.pivot.util.concurrent.Task;
import org.apache.pivot.util.concurrent.TaskExecutionException;
import org.apache.pivot.util.concurrent.TaskListener;
import org.apache.pivot.wtk.*;
import org.apache.solr.client.solrj.response.RangeFacet;

import javax.print.MultiDoc;
import java.io.*;
import java.net.URL;
import java.util.*;

public class DocumentsTab extends SplitPane implements Bindable {

  private int iNum;
  @BXML
  private TableView docTable;
  @BXML
  private TextInput docNum;
  @BXML
  private ListButton fieldsList;
  @BXML
  private TextInput termText;
  @BXML
  private Label dFreq;
  @BXML
  private Label tdMax;
  @BXML
  private Label maxDocs;
  @BXML
  private Label tFreq;
  @BXML
  private Label tdNum;
  @BXML
  private Label docNum2;
  @BXML
  private TextArea decText;

  @BXML
  private PushButton bPos;
  @BXML
  private PosAndOffsetsWindow posAndOffsetsWindow;

  @BXML
  private TermVectorDialog tvDialog;

  @BXML
  private DocValuesDialog dvDialog;

  @BXML
  private FieldDataWindow fieldDataWindow;

  @BXML
  private FieldNormWindow fieldNormWindow;

  @BXML
  private EditDocDialog editDocDialog;

  private java.util.List<String> fieldNames = null;

  // this gets injected by LukeWindow at init
  private LukeMediator lukeMediator;

  private Resources resources;

  private TermsEnum te;
  private PostingsEnum pe;

  private String fld;
  private Term lastTerm;
  private IndexReader ir;
  private java.util.List<String> indexFields;
  private IndexInfo idxInfo;
  private FieldInfos infos;

  public DocumentsTab() {

    Action.getNamedActions().put("nextDoc", new Action() {
      @Override
      public void perform(Component component) {
        nextDoc();
      }
    });
    Action.getNamedActions().put("prevDoc", new Action() {
      @Override
      public void perform(Component component) {
        prevDoc();
      }
    });
    //Action.getNamedActions().put("addDoc", new Action() {
    //  @Override
    //  public void perform(Component component) {
    //    addDoc();
    //  }
    //});
    //Action.getNamedActions().put("editDoc", new Action() {
    //  @Override
    //  public void perform(Component component) {
    //    editDoc();
    //  }
    //});
    Action.getNamedActions().put("showFirstTermDoc", new Action() {
      @Override
      public void perform(Component component) {
        showFirstTermDoc();
      }
    });
    Action.getNamedActions().put("deleteTermDoc", new Action() {
      @Override
      public void perform(Component component) {

      }
    });
    Action.getNamedActions().put("showFirstTerm", new Action() {
      @Override
      public void perform(Component component) {
        showFirstTerm();
      }
    });
    Action.getNamedActions().put("showTerm", new Action() {
      @Override
      public void perform(Component component) {
        prevDoc();
      }
    });
    Action.getNamedActions().put("showNextTerm", new Action() {
      @Override
      public void perform(Component component) {
        showNextTerm();
      }
    });
    Action.getNamedActions().put("showAllTermDoc", new Action() {
      @Override
      public void perform(Component component) {

      }
    });
    Action.getNamedActions().put("showNextTermDoc", new Action() {
      @Override
      public void perform(Component component) {
        showNextTermDoc();
      }
    });
    Action.getNamedActions().put("showPositions", new Action() {
      @Override
      public void perform(Component component) {

      }
    });

  }

  @Override
  public void initialize(Map<String,Object> namespace, URL location, Resources resources) {
    this.resources = resources;
  }

  public void initLukeMediator(LukeMediator lukeMediator) {
    this.lukeMediator = lukeMediator;
  }

  /**
   * Initialize GUI elements. This method is called when a new index is opened.
   * 
   * @throws Exception
   */
  public void onOpenIndex() throws Exception {
    this.idxInfo = lukeMediator.getIndexInfo();
    this.ir = idxInfo.getReader();
    if (ir instanceof CompositeReader) {
      this.infos = MultiFields.getMergedFieldInfos(ir);
    } else if (ir instanceof LeafReader) {
      //lr = (LeafReader) ir;
      this.infos = ((LeafReader)ir).getFieldInfos();
    }

    this.indexFields = idxInfo.getFieldNames();

    List<String> fields = new ArrayList<String>(indexFields.size());
    for (String s : indexFields) {
      fields.add(s);
    }
    fieldsList.setListData(fields);

    fieldNames = idxInfo.getFieldNames();
    if (fieldNames.size() == 0) {
      // TODO:
      // showStatus("Empty index.");
    } else {
      fieldsList.setSelectedIndex(0);
    }
    maxDocs.setText(String.valueOf(ir.maxDoc() - 1));

    bPos.setAction(new Action() {
      @Override
      public void perform(Component component) {
        showPositionsWindow();
      }
    });

    addlListenerToDocTable();
  }

  private void showDoc(int incr) {
    if (ir == null) {
      // showStatus(resources.get("documentsTab_noOrClosedIndex"));
      // TODO:
      System.out.println(resources.get("documentsTab_noOrClosedIndex"));
      return;
    }

    String num = docNum.getText();
    if (num.trim().equals(""))
      num = String.valueOf(-incr);
    try {
      iNum = Integer.parseInt(num);
      iNum += incr;
      if (iNum < 0 || iNum >= ir.maxDoc()) {
        System.out.println(resources.get("documentsTab_docNumOutsideRange"));
        // TODO:
        // showStatus(resources.get("documentsTab_docNumOutsideRange"));
        return;
      }
      docNum.setText(String.valueOf(iNum));

      pe = null;
      tdNum.setText("?");
      tFreq.setText("?");
      tdMax.setText("?");

      Bits live = MultiFields.getLiveDocs(ir);
      if (live == null || live.get(iNum)) {
        Task<Object> populateTableTask = new Task<Object>() {

          @Override
          public Object execute() throws TaskExecutionException {
            return null;
          }
        };
        TaskListener<Object> tl = new TaskListener<Object>() {
          @Override
          public void taskExecuted(Task<Object> task) {
            try {
              Document doc = ir.document(iNum);
              popTableWithDoc(iNum, doc);
            } catch (Exception e) {
              e.printStackTrace();
              // TODO:
              // showStatus(e.getMessage());
            }
          }

          @Override
          public void executeFailed(Task<Object> task) {

          }
        };
        populateTableTask.execute(new TaskAdapter<Object>(tl));

      } else {

        Task<Object> populateTableTask = new Task<Object>() {

          @Override
          public Object execute() throws TaskExecutionException {
            return null;
          }
        };
        TaskListener<Object> tl = new TaskListener<Object>() {
          @Override
          public void taskExecuted(Task<Object> task) {

            // TODO:
            // showStatus("Deleted document - not available.");
            popTableWithDoc(iNum, null);
          }

          @Override
          public void executeFailed(Task<Object> task) {

          }
        };
        populateTableTask.execute(new TaskAdapter<Object>(tl));

      }
    } catch (Exception e) {
      e.printStackTrace();
      // TODO:
      // showStatus(e.getMessage());
    }
  }

  private void nextDoc() {
    showDoc(1);
  }

  private void prevDoc() {
    showDoc(-1);
  }

  /*
  private void addDoc() {
    System.out.println("addDoc()");
  }

  private void editDoc() { showEditDocWindow(); }

  private void showEditDocWindow() {
    try {
      editDocDialog.initDocumentInfo(iNum, ir, lukeMediator);
      editDocDialog.open(getDisplay(), getWindow());
    } catch (Exception e) {
      // TODO
      e.printStackTrace();
    }
  }
  */

  public void popTableWithDoc(int docid, Document doc) {
    docNum.setText(String.valueOf(docid));
    List<Map<String,Object>> tableData = new ArrayList<Map<String,Object>>();
    docTable.setTableData(tableData);

    // putProperty(table, "doc", doc);
    // putProperty(table, "docNum", new Integer(docid));
    if (doc == null) {
      docNum2.setText(String.valueOf(docid) + "  DELETED");
      return;
    }

    docNum2.setText(String.valueOf(docid));
    for (int i = 0; i < indexFields.size(); i++) {
      IndexableField[] fields = doc.getFields(indexFields.get(i));
      if (fields == null || fields.length == 0) {
        Map<String,Object> row = new HashMap<String,Object>();
        tableData.add(row);
        addFieldRow(row, indexFields.get(i), null, docid);
        continue;
      }
      for (int j = 0; j < fields.length; j++) {
        // if (fields[j].isBinary()) {
        // System.out.println("f.len=" + fields[j].getBinaryLength() +
        // ", doc.len=" + doc.getBinaryValue(indexFields[i]).length);
        // }
        Map<String,Object> row = new HashMap<String,Object>();
        tableData.add(row);
        addFieldRow(row, indexFields.get(i), fields[j], docid);
      }
    }
  }

  private static final String FIELDROW_KEY_NAME = "name";
  private static final String FIELDROW_KEY_FLAGS = "flags";
  private static final String FIELDROW_KEY_NORM = "norm";
  private static final String FIELDROW_KEY_DOCVALUE = "docvalue";
  private static final String FIELDROW_KEY_VALUE = "value";
  private static final String FIELDROW_KEY_FIELD = "field";

  private void addFieldRow(Map<String,Object> row, String fName, IndexableField field, int docid) {
    java.util.Map<String,Decoder> decoders = lukeMediator.getDecoders();
    Decoder defDecoder = lukeMediator.getDefDecoder();

    Field f = (Field) field;

    row.put(FIELDROW_KEY_FIELD, field);

    if (fName != null) {
      row.put(FIELDROW_KEY_NAME, fName);
      row.put(FIELDROW_KEY_FLAGS, Util.fieldFlags(f, infos.fieldInfo(fName)));
      FieldInfo info = infos.fieldInfo(fName);
      try {
        if (info.hasNorms()) {
          NumericDocValues norms = MultiDocValues.getNormValues(ir, info.name);
          String norm = String.valueOf(norms.get(docid));
          row.put(FIELDROW_KEY_NORM, norm);
        } else {
          row.put(FIELDROW_KEY_NORM, "-");
        }
      } catch (IOException ioe) {
        ioe.printStackTrace();
        row.put(FIELDROW_KEY_NORM, "?");
      }
      try {
        StringBuilder sb = new StringBuilder();
        switch (info.getDocValuesType()) {
          case BINARY:
            BinaryDocValues binValues = MultiDocValues.getBinaryValues(ir, fName);
            sb.append(binValues.get(docid).utf8ToString());
            break;
          case NUMERIC:
            NumericDocValues numValues = MultiDocValues.getNumericValues(ir, fName);
            sb.append(String.valueOf(numValues.get(docid)));
            break;
          case SORTED:
            SortedDocValues sortedValues = MultiDocValues.getSortedValues(ir, fName);
            sb.append(sortedValues.get(docid).utf8ToString());
            break;
          case SORTED_NUMERIC:
            SortedNumericDocValues sortedNumValues = MultiDocValues.getSortedNumericValues(ir, fName);
            sortedNumValues.setDocument(docid);
            for (int i = 0; i < sortedNumValues.count(); i++) {
              if (i > 0) sb.append(",");
              sb.append(String.valueOf(sortedNumValues.valueAt(i)));
            }
            break;
          case SORTED_SET:
            SortedSetDocValues sortedSetDocValues = MultiDocValues.getSortedSetValues(ir, fName);
            sortedSetDocValues.setDocument(docid);
            long ord;
            while ((ord = sortedSetDocValues.nextOrd()) != SortedSetDocValues.NO_MORE_ORDS) {
              if (sb.length() > 0) sb.append(",");
              sb.append(sortedSetDocValues.lookupOrd(ord).utf8ToString());
            }
            break;
          default:
            row.put(FIELDROW_KEY_DOCVALUE, "-");
        }
        row.put(FIELDROW_KEY_DOCVALUE, sb.toString());
      } catch (IOException e) {
        e.printStackTrace();
        row.put(FIELDROW_KEY_DOCVALUE, "-");
      }
    } else {
      row.put(FIELDROW_KEY_NORM, "-");
      row.put(FIELDROW_KEY_DOCVALUE, "-");
    }

    if (f != null) {
      String text = f.stringValue();
      if (text == null) {
        if (f.binaryValue() != null) {
          text = Util.bytesToHex(f.binaryValue(), false);
        } else {
          text = "(null)";
        }
      }
      Decoder dec = decoders.get(f.name());
      if (dec == null)
        dec = defDecoder;
      try {
        if (f.fieldType().stored()) {
          text = dec.decodeStored(f.name(), f);
        } else {
          text = dec.decodeTerm(f.name(), f.binaryValue());
        }
      } catch (Throwable e) {
        // TODO:
        // setColor(cell, "foreground", Color.RED);
      }
      row.put(FIELDROW_KEY_VALUE, Util.escape(text));
    } else {
      row.put(FIELDROW_KEY_VALUE, "<not present or not stored>");
    }
  }

  public void showFirstTerm() {
    if (ir == null) {
      // TODO:
      // showStatus(MSG_NOINDEX);
      return;
    }
    Task<Object> showFirstTermTask = new Task<Object>() {

      @Override
      public Object execute() throws TaskExecutionException {
        return null;
      }
    };

    TaskListener<Object> taskListener = new TaskListener<Object>() {
      @Override
      public void taskExecuted(Task<Object> task) {
        try {

          fld = (String) fieldsList.getSelectedItem();
          //System.out.println("fld:" + fld);
          Terms terms = MultiFields.getTerms(ir, fld);
          te = terms.iterator();
          BytesRef term = te.next();
          showTerm(new Term(fld, term));
        } catch (Exception e) {
          e.printStackTrace();
          // TODO:
          // showStatus(e.getMessage());
        }
        // ai.setActive(false);
      }

      @Override
      public void executeFailed(Task<Object> task) {
        // ai.setActive(false);
      }
    };

    showFirstTermTask.execute(new TaskAdapter<Object>(taskListener));

  }

  public void showFirstTermDoc() {

    if (lastTerm == null)
      return;
    if (ir == null) {
      // TODO:
      // showStatus(resources.get("documentsTab_noOrClosedIndex"));
      return;
    }
    Task<Object> showFirstTermDocTask = new Task<Object>() {

      @Override
      public Object execute() throws TaskExecutionException {
        return null;
      }
    };

    TaskListener<Object> taskListener = new TaskListener<Object>() {
      @Override
      public void taskExecuted(Task<Object> task) {
        try {
          PostingsEnum pe = MultiFields.getTermDocsEnum(ir, lastTerm.field(), lastTerm.bytes());
          pe.nextDoc();
          tdNum.setText("1");
          DocumentsTab.this.pe = pe;
          showTermDoc();
        } catch (Exception e) {
          e.printStackTrace();
          // TODO:
          // showStatus(e.getMessage());
        }
        // ai.setActive(false);
      }

      @Override
      public void executeFailed(Task<Object> task) {
        // ai.setActive(false);
      }
    };

    showFirstTermDocTask.execute(new TaskAdapter<Object>(taskListener));

  }

  public void showNextTermDoc() {
    if (lastTerm == null)
      return;
    if (ir == null) {
      // TODO:
      // showStatus(MSG_NOINDEX);
      return;
    }
    Task<Object> showNextTermDocTask = new Task<Object>() {

      @Override
      public Object execute() throws TaskExecutionException {
        return null;
      }
    };

    TaskListener<Object> taskListener = new TaskListener<Object>() {
      @Override
      public void taskExecuted(Task<Object> task) {
        try {
          if (pe == null) {
            showFirstTermDoc();
            return;
          }
          if (pe.nextDoc() == PostingsEnum.NO_MORE_DOCS)
            return;

          String sCnt = tdNum.getText();
          int cnt = 1;
          try {
            cnt = Integer.parseInt(sCnt);
          } catch (Exception e) {}

          tdNum.setText(String.valueOf(cnt + 1));

          showTermDoc();
        } catch (Exception e) {
          e.printStackTrace();
          // TODO:
          // showStatus(e.getMessage());
        }
        // ai.setActive(false);
      }

      @Override
      public void executeFailed(Task<Object> task) {
        // ai.setActive(false);
      }
    };

    showNextTermDocTask.execute(new TaskAdapter<Object>(taskListener));

  }

  protected void showTerm(final Term t) {
    if (t == null) {
      // TODO:
      // showStatus("No terms?!");
      return;
    }
    if (ir == null) {
      // TODO:
      // showStatus(resources.get("documentsTab_noOrClosedIndex"));
      return;
    }

    lastTerm = t;

    fieldsList.setSelectedItem(t.field());
    Decoder dec = lukeMediator.getDecoders().get(t.field());

    if (dec == null)
      dec = lukeMediator.getDefDecoder();
    String s = null;
    boolean decodeErr = false;
    try {
      //s = dec.decodeTerm(t.field(), t.text());
      s = dec.decodeTerm(t.field(), t.bytes());
    } catch (Throwable e) {
      s = e.getMessage();
      decodeErr = true;
    }

    termText.setText(t.text());

    if (!s.equals(t.text())) {
      String decoded = s + " (by " + dec.toString() + ")";
      decText.setText(decoded);

      if (decodeErr) {
        // setColor(rawText, "foreground", Color.RED);
      } else {
        // setColor(rawText, "foreground", Color.BLUE);
      }
    } else {
      decText.setText("");
      // setColor(rawText, "foreground", Color.BLACK);
    }

    lastTerm = t;
    // putProperty(fText, "decText", s);

    pe = null;
    tdNum.setText("?");
    tFreq.setText("?");

    Task<Object> updateDfTdTask = new Task<Object>() {

      @Override
      public Object execute() throws TaskExecutionException {
        return null;
      }
    };

    TaskListener<Object> taskListener = new TaskListener<Object>() {
      @Override
      public void taskExecuted(Task<Object> task) {

        try {
          int freq = ir.docFreq(t);
          tdMax.setText(String.valueOf(freq));
        } catch (Exception e) {
          e.printStackTrace();
          // TODO:
          // showStatus(e.getMessage());
        }
        // ai.setActive(false);
      }

      @Override
      public void executeFailed(Task<Object> task) {
        // ai.setActive(false);
      }
    };

    updateDfTdTask.execute(new TaskAdapter<Object>(taskListener));

  }

  public void showNextTerm() {
    if (ir == null) {
      // TODO:
      // showStatus(resources.get("documentsTab_noOrClosedIndex"));
      return;
    }
    Task<Object> showNextTermTask = new Task<Object>() {

      @Override
      public Object execute() throws TaskExecutionException {
        return null;
      }
    };

    TaskListener<Object> taskListener = new TaskListener<Object>() {
      @Override
      public void taskExecuted(Task<Object> task) {
        try {
          String text;
          text = termText.getText();
          if (text == null || text.trim().length() == 0) {
            showFirstTerm();
            return;
          }

          String fld = (String) fieldsList.getSelectedItem();

          SeekStatus status;
          BytesRef rawTerm = null;
          if (te != null) {
            rawTerm = te.term();
          }
          String rawString = rawTerm != null ? rawTerm.utf8ToString() : null;

          if (te == null || !DocumentsTab.this.fld.equals(fld) || !text.equals(rawString)) {
            // seek for requested term
            Terms terms = MultiFields.getTerms(ir, fld);
            te = terms.iterator();

            DocumentsTab.this.fld = fld;
            status = te.seekCeil(new BytesRef(text));
            if (status.equals(SeekStatus.FOUND) || status.equals(SeekStatus.NOT_FOUND)) {
              // precise term or different term after the requested term was found.
              rawTerm = te.term();
            } else {
              rawTerm = null;
            }
          } else {
            // move to next term
            rawTerm = te.next();
          }
          if (rawTerm == null) { // proceed to next field
            int idx = fieldNames.indexOf(fld);
            while (idx < fieldNames.size() - 1) {
              idx++;
              fieldsList.setSelectedIndex(idx);
              fld = fieldNames.get(idx);
              Terms terms = MultiFields.getTerms(ir, fld);
              if (terms == null) {
                continue;
              }
              te = terms.iterator();
              rawTerm = te.next();
              DocumentsTab.this.fld = fld;
              //break;
            }
          }
          if (rawTerm == null) {
            // TODO:
            // showStatus("No more terms");
            return;
          }

          showTerm(new Term(fld, rawTerm));
        } catch (Exception e) {
          e.printStackTrace();
          // TODO:
          // showStatus(e.getMessage());
        }
        // ai.setActive(false);
      }

      @Override
      public void executeFailed(Task<Object> task) {
        // ai.setActive(false);
      }
    };

    showNextTermTask.execute(new TaskAdapter<Object>(taskListener));

  }

  private void showTermDoc() {
    if (ir == null) {
      // TODO:
      // showStatus(resources.get("documentsTab_noOrClosedIndex"));
      return;
    }
    Task<Object> showTermDocTask = new Task<Object>() {

      @Override
      public Object execute() throws TaskExecutionException {
        return null;
      }
    };

    TaskListener<Object> taskListener = new TaskListener<Object>() {
      @Override
      public void taskExecuted(Task<Object> task) {
        try {
          Document doc = ir.document(pe.docID());
          docNum.setText(String.valueOf(pe.docID()));
          iNum = pe.docID();

          tFreq.setText(String.valueOf(pe.freq()));

          popTableWithDoc(pe.docID(), doc);
        } catch (Exception e) {
          e.printStackTrace();
          // TODO:
          // showStatus(e.getMessage());
        }

        // ai.setActive(false);
      }

      @Override
      public void executeFailed(Task<Object> task) {
        // ai.setActive(false);
      }
    };

    showTermDocTask.execute(new TaskAdapter<Object>(taskListener));

  }

  private void showPositionsWindow() {
    try {
      if (pe == null) {
        Alert.alert(MessageType.WARNING, (String) resources.get("documentsTab_msg_docNotSelected"), getWindow());
      } else {
        // create new Enum to show positions info
        PostingsEnum pe2 = MultiFields.getTermPositionsEnum(ir, lastTerm.field(), lastTerm.bytes());
        if (pe2 == null) {
          Alert.alert(MessageType.INFO, (String) resources.get("documentsTab_msg_positionNotIndexed"), getWindow());
        } else {
          // enumerate docId to the current doc
          while(pe2.docID() != pe.docID()) {
            if (pe2.nextDoc() == DocIdSetIterator.NO_MORE_DOCS) {
              // this must not happen!
              Alert.alert(MessageType.ERROR, (String) resources.get("documentsTab_msg_noPositionInfo"), getWindow());
            }
          }
          try {
            posAndOffsetsWindow.initPositionInfo(pe2, lastTerm);
            posAndOffsetsWindow.open(getDisplay(), getWindow());
          } catch (Exception e) {
            // TODO:
            e.printStackTrace();
          }
        }
      }
    } catch (Exception e) {
      // TODO
      e.printStackTrace();
    }
  }

  public void showAllTermDoc() {
    final IndexReader ir = lukeMediator.getIndexInfo().getReader();
    if (ir == null) {
      // TODO:
      // showStatus("MSG_NOINDEX");
      return;
    }
    // Object tabpane = find("maintpane");
    // setInteger(tabpane, "selected", 2);
    // Object qField = find("qField");
    // setString(qField, "text", t.field() + ":" + t.text());
    // Object qFieldParsed = find("qFieldParsed");
    // Object ckScoreRes = find("ckScoreRes");
    // Object ckOrderRes = find("ckOrderRes");
    // Object cntRepeat = find("cntRepeat");
    // final boolean scoreRes = getBoolean(ckScoreRes, "selected");
    // final boolean orderRes = getBoolean(ckOrderRes, "selected");
    // final int repeat = Integer.parseInt(getString(cntRepeat, "text"));
    final Query q = new TermQuery(lastTerm);
    // parsedQueryLabel.setText(q.toString());
    Task<Object> showAllTermDocTask = new Task<Object>() {

      @Override
      public Object execute() throws TaskExecutionException {
        return null;
      }
    };

    TaskListener<Object> taskListener = new TaskListener<Object>() {
      @Override
      public void taskExecuted(Task<Object> task) {
        IndexSearcher is = null;
        try {
          is = new IndexSearcher(ir);
          // Object sTable = find("sTable");
          // removeAll(sTable);
          // AllHitsCollector ahc = new AllHitsCollector(orderRes, scoreRes);
          // _search(q, is, ahc, sTable, repeat);
        } catch (Exception e) {
          e.printStackTrace();
          // TODO:
          // errorMsg(e.getMessage());
        }
        // ai.setActive(false);
        // lukeWindow.setEnabled(true);
      }

      @Override
      public void executeFailed(Task<Object> task) {
        // ai.setActive(false);
        // lukeWindow.setEnabled(true);
      }
    };

    showAllTermDocTask.execute(new TaskAdapter<Object>(taskListener));

  }

  private void addlListenerToDocTable() {
    docTable.getComponentMouseButtonListeners().add(new ComponentMouseButtonListener.Adapter() {
      @Override
      public boolean mouseClick(Component component, Mouse.Button button, int i, int i1, int i2) {
        final Map<String, Object> row = (Map<String, Object>) docTable.getSelectedRow();
        if (row == null) {
          System.out.println("No field selected.");
          return false;
        }

        final String name = (String)row.get(FIELDROW_KEY_NAME);
        final IndexableField field = (IndexableField)row.get(FIELDROW_KEY_FIELD);
        final FieldInfo info = infos.fieldInfo(name);

        if (button.name().equals(Mouse.Button.RIGHT.name())) {
          MenuPopup popup = new MenuPopup();
          Menu menu = new Menu();
          Menu.Section section = new Menu.Section();
          Menu.Item item1 = new Menu.Item(resources.get("documentsTab_docTable_popup_menu1"));
          try {
            final Terms terms = ir.getTermVector(iNum, name);
            item1.setAction(new Action() {
              @Override
              public void perform(Component component) {
                showTermVectorWindow(name, terms);
              }
            });
            if (terms == null) {
              // disable the action if term vector for this field is not available
              item1.getAction().setEnabled(false);
            }
          } catch (IOException e) {
            // TODO
            e.printStackTrace();
          }

          Menu.Item item2 = new Menu.Item(resources.get("documentsTab_docTable_popup_menu2"));
          item2.setAction(new Action() {
            @Override
            public void perform(Component component) {
              showDocValuesDialog(info);
            }
          });
          if (info.getDocValuesType() == DocValuesType.NONE) {
            // disable the action if doc values are not available for this field.
            item2.getAction().setEnabled(false);
          }

          Menu.Item item3 = new Menu.Item(resources.get("documentsTab_docTable_popup_menu3"));
          item3.setAction(new Action() {
            @Override
            public void perform(Component component) {
              showFieldDataWindow(name, field);
            }
          });
          if (field == null) {
            // disable the action if field data is not available for this field
            item3.getAction().setEnabled(false);
          }

          Menu.Item item4 = new Menu.Item(resources.get("documentsTab_docTable_popup_menu4"));
          item4.setAction(new Action() {
            @Override
            public void perform(Component component) {
              if (field == null) {
                Alert.alert(MessageType.WARNING, (String) resources.get("documentsTab_msg_noDataAvailable"), getWindow());
              } else if (info.getIndexOptions() == null || !info.hasNorms()) {
                Alert.alert(MessageType.WARNING, (String) resources.get("documentsTab_msg_noNorm"), getWindow());
              } else {
                showFieldNormWindow(name);
              }
            }
          });
          if (field == null || info.getIndexOptions() == null || !info.hasNorms()) {
            // disable the action if norm is not available for this field
            item4.getAction().setEnabled(false);
          }

          Menu.Item item5 = new Menu.Item(resources.get("documentsTab_docTable_popup_menu5"));
            item5.setAction(new Action() {
              @Override
              public void perform(Component component) {
                if (field != null) {
                  saveFieldData(field);
                } else if (info.getDocValuesType() != DocValuesType.NONE) {
                  try {
                    saveDocValuesData(info);
                  } catch (IOException e) {
                    // TODO
                    e.printStackTrace();
                  }
                }
              }
            });
          if (field == null && info.getDocValuesType() == DocValuesType.NONE) {
            // disable the action if both of field data and doc values are not available for this field
            item5.getAction().setEnabled(false);
          }

          section.add(item1);
          section.add(item2);
          section.add(item3);
          section.add(item4);
          section.add(item5);
          menu.getSections().add(section);
          popup.setMenu(menu);
          popup.open(getWindow(), getMouseLocation().x + 20, getMouseLocation().y + 50);
          return true;
        }
        return false;
      }
    });

  }

  private void showTermVectorWindow(String fieldName, Terms tv) {
    try {
      tvDialog.initTermVector(fieldName, tv);
    } catch (IOException e) {
      // TODO
      e.printStackTrace();
    }
    tvDialog.open(getDisplay(), getWindow());
  }

  private void showDocValuesDialog(FieldInfo finfo) {
    try {
      dvDialog.initDocValues(finfo, ir, iNum);
    } catch (IOException e) {
      // TODO
      e.printStackTrace();
    }
    dvDialog.open(getDisplay(), getWindow());
  }

  private void showFieldDataWindow(String fieldName, IndexableField field) {
    fieldDataWindow.initFieldData(fieldName, field);
    fieldDataWindow.open(getDisplay(), getWindow());
  }

  //private static BM25Similarity defaultSimilarity = new BM25Similarity();
  private void showFieldNormWindow(String fieldName) {
    if (ir != null) {
      try {
        NumericDocValues norms = MultiDocValues.getNormValues(ir, fieldName);
        fieldNormWindow.initFieldNorm(iNum, fieldName, norms);
        fieldNormWindow.open(getDisplay(), getWindow());
      } catch (Exception e) {
        Alert.alert(MessageType.ERROR, (String) resources.get("documentsTab_msg_errorNorm"), getWindow());
        e.printStackTrace();
      }
    }
  }

  private void saveFieldData(IndexableField field) {
    byte[] data = null;
    if (field.binaryValue() != null) {
      BytesRef bytes = field.binaryValue();
      data = new byte[bytes.length];
      System.arraycopy(bytes.bytes, bytes.offset, data, 0, bytes.length);
    }
    else {
      try {
        data = field.stringValue().getBytes("UTF-8");
      } catch (UnsupportedEncodingException uee) {
        uee.printStackTrace();
        data = field.stringValue().getBytes();
      }
    }
    if (data == null || data.length == 0) {
      Alert.alert(MessageType.WARNING, (String) resources.get("documentsTab_msg_noDataAvailable"), getWindow());
    }

    saveDataToLocalFile(data);
  }

  private void saveDocValuesData(FieldInfo finfo) throws IOException {
    byte[] data = null;
    BytesRef bytes;
    StringBuilder sb;
    switch(finfo.getDocValuesType()) {
      case BINARY:
        BinaryDocValues bdv = MultiDocValues.getBinaryValues(ir, finfo.name);
        bytes = bdv.get(iNum);
        data = new byte[bytes.length];
        System.arraycopy(bytes.bytes, bytes.offset, data, 0, bytes.length);
        break;
      case NUMERIC:
        NumericDocValues ndv = MultiDocValues.getNumericValues(ir, finfo.name);
        data = String.valueOf(ndv.get(iNum)).getBytes();
        break;
      case SORTED:
        SortedDocValues sdv = MultiDocValues.getSortedValues(ir, finfo.name);
        bytes = sdv.get(iNum);
        data = new byte[bytes.length];
        System.arraycopy(bytes.bytes, bytes.offset, data, 0, bytes.length);
        break;
      case SORTED_NUMERIC:
        SortedNumericDocValues sndv = MultiDocValues.getSortedNumericValues(ir, finfo.name);
        sndv.setDocument(iNum);
        sb = new StringBuilder();
        for (int i = 0; i < sndv.count(); i++) {
          if (i > 0) sb.append("\n");
          sb.append(sndv.valueAt(i));
        }
        data = sb.toString().getBytes();
        break;
      case SORTED_SET:
        SortedSetDocValues ssdv = MultiDocValues.getSortedSetValues(ir, finfo.name);
        ssdv.setDocument(iNum);
        sb = new StringBuilder();
        long ord;
        while ((ord = ssdv.nextOrd()) != SortedSetDocValues.NO_MORE_ORDS) {
          if (sb.length() > 0) sb.append("\n");
          sb.append(ssdv.lookupOrd(ord).utf8ToString());
        }
        data = sb.toString().getBytes();
        break;
    }

    if (data == null || data.length == 0) {
      Alert.alert(MessageType.WARNING, (String) resources.get("documentsTab_msg_noDocValuesDataAvailable"),
          getWindow());
    }

    saveDataToLocalFile(data);
  }

  private void saveDataToLocalFile(final byte[] data) {
    final FileBrowserSheet fileBrowserSheet = new FileBrowserSheet(FileBrowserSheet.Mode.SAVE_AS);
    fileBrowserSheet.open(getWindow(), new SheetCloseListener() {
      @Override
      public void sheetClosed(Sheet sheet) {
        if (sheet.getResult()) {
          Sequence<File> selectedFiles = fileBrowserSheet.getSelectedFiles();
          File file = selectedFiles.get(0);
          try {
            OutputStream os = new FileOutputStream(file);
            int delta = data.length / 100;
            if (delta == 0) delta = 1;
            for (int i = 0; i < data.length; i++) {
              os.write(data[i]);
              // TODO: show progress
              //if (i % delta == 0) {
              // setInteger(bar, "value", i / delta);
              //}
            }
            os.flush();
            os.close();
            Alert.alert(MessageType.INFO, "Saved to " + file.getAbsolutePath(), getWindow());
          } catch (IOException e) {
            e.printStackTrace();
            Alert.alert(MessageType.ERROR, "Can't save to : " + file.getAbsoluteFile(), getWindow());
          }
        } else {
          Alert.alert(MessageType.INFO, "You didn't select anything.", getWindow());
        }

      }
    });
  }
}