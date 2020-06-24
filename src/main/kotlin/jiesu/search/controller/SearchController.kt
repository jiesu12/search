package jiesu.search.controller

import jiesu.search.service.SearchService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping
class SearchController(val searchService: SearchService) {

    @PostMapping
    fun index(@RequestParam indexName: String, @RequestParam path: String, @RequestBody content: String) =
            searchService.index(indexName, path, content)

    @GetMapping("/api")
    fun search(@RequestParam indexName: String,
               @RequestParam terms: String,
               @RequestParam pageIndex: Int,
               @RequestParam pageSize: Int) =
            searchService.search(indexName, terms, pageIndex, pageSize)

    @DeleteMapping
    fun delete(@RequestParam indexName: String, @RequestParam path: String) =
            searchService.delete(indexName, path)

    @DeleteMapping("/all")
    fun deleteAll(@RequestParam indexName: String) = searchService.deleteAll(indexName)

}