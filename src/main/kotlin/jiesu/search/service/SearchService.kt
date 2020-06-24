package jiesu.search.service

import com.google.common.collect.Lists
import jiesu.search.model.SearchResult
import jiesu.search.model.SearchResults
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.TokenStream
import org.apache.lucene.analysis.Tokenizer
import org.apache.lucene.analysis.core.LowerCaseFilter
import org.apache.lucene.analysis.standard.StandardFilter
import org.apache.lucene.analysis.util.CharTokenizer
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.StringField
import org.apache.lucene.document.TextField
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.index.IndexWriterConfig.OpenMode
import org.apache.lucene.index.Term
import org.apache.lucene.queryparser.classic.QueryParser
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.TopScoreDocCollector
import org.apache.lucene.search.highlight.Highlighter
import org.apache.lucene.search.highlight.QueryScorer
import org.apache.lucene.search.highlight.SimpleFragmenter
import org.apache.lucene.search.highlight.SimpleHTMLFormatter
import org.apache.lucene.store.Directory
import org.apache.lucene.store.FSDirectory
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.io.IOException

@Service
class SearchService(val indexDir: File) {
    private val FIELD_NAME = "name"
    private val FIELD_PATH = "path"
    private val FIELD_CONTENT = "contents"

    companion object {
        val log: Logger = LoggerFactory.getLogger(SearchService::class.java)
    }

    fun index(indexName: String, filePath: String, content: String) {
        getIndexWriter(indexName).use { writer ->
            // make a new, empty document
            val doc = Document()

            // TextField will be tokenized, i.e, indexed. StringField
            // doesn't get tokenized. Must use StringField, so that later we
            // can delete the document using "path" term.
            doc.add(StringField(FIELD_PATH, filePath, Field.Store.YES))

            // Use TextField for "name", it will split the name into words.
            doc.add(TextField(FIELD_NAME, filePath, Field.Store.YES))

            // content will be split into words.
            doc.add(TextField(FIELD_CONTENT, content, Field.Store.YES))

            if (writer.config.openMode == OpenMode.CREATE) {
                // New index, add the document
                try {
                    writer.addDocument(doc)
                } catch (e: Exception) {
                    log.warn("Failed to index file {} in {}", filePath, indexName, e)
                }
            } else {
                // Existing index (an old copy of this document may have
                // been indexed) so we use updateDocument to replace
                // the old one matching the exact path, if present:
                try {
                    writer.updateDocument(Term(FIELD_PATH, filePath), doc)
                } catch (e: Exception) {
                    log.warn("Failed to update index for {} in {}", filePath, indexName, e)
                }
            }
        }
    }

    fun search(indexName: String, terms: String, pageIndex: Int, pageSize: Int): SearchResults {
        val decoratedTerm = decorateSearchTerm(terms)
        DirectoryReader.open(FSDirectory.open(indexDir.toPath().resolve(indexName))).use { reader ->
            val searcher = IndexSearcher(reader)
            val analyzer: Analyzer = LetterNumberAnalyzer()
            val parser = QueryParser(FIELD_CONTENT, analyzer)
            val query = parser.parse(decoratedTerm)
            val collector = TopScoreDocCollector.create(1000)
            searcher.search(query, collector)
            val topDocs = collector.topDocs(pageSize * pageIndex, pageSize)
            val hits = topDocs.scoreDocs
            val results: MutableList<SearchResult> = Lists.newArrayList<SearchResult>()
            val highlighter = Highlighter(SimpleHTMLFormatter("FSSRHL_", "_FSSRHL"), QueryScorer(query, FIELD_CONTENT))
            highlighter.textFragmenter = SimpleFragmenter()
            for (hit in hits) {
                val doc = searcher.doc(hit.doc)
                val path: String = doc[FIELD_PATH]
                val contents = doc[FIELD_CONTENT]
                if (contents == null) {
                    results.add(SearchResult(path, null))
                } else {
                    val fragment = highlighter.getBestFragment(analyzer, FIELD_CONTENT, contents)
                    results.add(SearchResult(path, fragment))
                }
            }
            return SearchResults(results, collector.totalHits)
        }
    }

    fun delete(indexName: String, filePath: String) {
        try {
            getIndexWriter(indexName).use { writer ->
                log.debug("deleting search index of {}", filePath)
                writer.deleteDocuments(Term(FIELD_PATH, filePath))
            }
        } catch (e: IOException) {
            log.error("Failed to delete search index for {} in {}", filePath, indexName, e)
        }
    }

    fun deleteAll(indexName: String) {
        getIndexWriter(indexName).use {  it.deleteAll() }
    }

    private fun getIndexWriter(indexName: String): IndexWriter {
        val analyzer: Analyzer = LetterNumberAnalyzer()
        val indexWriterConfig = IndexWriterConfig(analyzer)
        indexWriterConfig.openMode = OpenMode.CREATE_OR_APPEND
        val indexDirectory: Directory = FSDirectory.open(indexDir.toPath().resolve(indexName))
        return IndexWriter(indexDirectory, indexWriterConfig)
    }

    private fun decorateSearchTerm(term: String): String {
        val words = term.split("[ ]+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val sb = StringBuilder()
        for (word in words) {
            sb.append("(")
            sb.append("name:")
            sb.append(word)
            sb.append(" OR ")
            sb.append(word)
            sb.append(") AND ")
        }
        sb.delete(sb.length - 5, sb.length)
        return sb.toString()
    }
}

internal class LetterNumberTokenizer : CharTokenizer() {
    override fun isTokenChar(c: Int): Boolean {
        return Character.isLetter(c) || Character.isDigit(c)
    }
}

internal class LetterNumberAnalyzer : Analyzer() {
    override fun createComponents(fieldName: String): TokenStreamComponents {
        val tokenizer: Tokenizer = LetterNumberTokenizer()
        var filter: TokenStream = StandardFilter(tokenizer)
        // need a lower case filter so that the searching is case
        // insensitive.
        filter = LowerCaseFilter(filter)
        return TokenStreamComponents(tokenizer, filter)
    }
}