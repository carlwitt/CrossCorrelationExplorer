------------------------------------------------------------------------------------------------------------------------
12.5.2014 Normalized cross correlation algorithm.
------------------------------------------------------------------------------------------------------------------------

Project setup in IntelliJ. 
Reviewed some test files.

Prepared test data for the new (normalized) cross correlation computation using Patricks Formula and a reference R computation.
The result is the same for a zero time lag. For the others, R doesn't seem to wrap the input series.

Preprocess time series B under consideration, i.e. calculate mean and variance for all used windows.
Within a single cross-correlation, the only improvement for time series A is an incremental mean.
Among many cross-correlation the means and variances, as well as the normalized windows can be reused.

Preprocessing time series A with all possible data is useful when computing the cross correlation to other time series.
For the time series of set B, calculate only mean and variance because the results cannot be removed from memory that quickly.

The number of windows is linear in the length of the time series (N - |w| + 1)
So the number of normalized values for a window size of N/100 roughly N^2/100.
For N = 1e5 this is 1e8 which surely is prohibitively large, even for a relatively small window size.
Even for smaller windows, the quadratic term dominates and the number of values is large.

An object containing the precomputed data might be useful. It can be easily discarded to free memory.
Since all precomputed data is specific to a certain window size this might an even cleaner solution.

------------------------------------------------------------------------------------------------------------------------
16.5.2014 
------------------------------------------------------------------------------------------------------------------------

Profiling has shown that each correlogram (with standard parameters) takes about 400kb.
Consequently, computing all partial results and then aggregating is not always possible.
For 500x500 time series, 100GB memory would be necessary, for 1000x1000 400 GB (x2 -> x4).
Consequently a cache is of very limited use. On the other hand, if the memory is not needed for caching,
precomputing all necessary terms for cross correlation might be possible to get close to the theoretical speedup of 3-4x

------------------------------------------------------------------------------------------------------------------------
19.5.2014 
------------------------------------------------------------------------------------------------------------------------

Even with very few time series (1 x 3) the time series plot becomes chaotic. 
Some aggregation (mean/median) would be sensible. But using the correlation results, we can aggregate intelligently.
E.g. show only those that correlate strongly/significantly and use their mean/median.
   
- changing input files with loaded time series is buggy (can't unload nonexisting and range determination fails in ts view)
- working with the single time series is relatively difficult (e.g. remove a time series from the view: no link between view and set A list)

Tried having a constant color range of -1,1 for the mean and standard deviation. (if all means are distributed between -1 and 1,
the maximum standard deviation possible is 1).
The advantage is that it becomes immediately clear from the legend that no strong correlations are present,
whereas an adaptive color scale can give false impressions with dark colors (which are mapped, for instance, to a value of 0,3).
Then again, for very large windows, a value of 0.3 might be a strong correlation, so the best would be to add the significance to the
coloring. Then, dark colors would represent significant correlations.
For the correlogram view this means that cells with a significant mean will become visible.
The backdraw is probably that this will be confusing with the number of significant correlations in one cell.


------------------------------------------------------------------------------------------------------------------------
Milestone 26.5. 
------------------------------------------------------------------------------------------------------------------------

√ Aktualisierung und Beschleunigung der Visualisierung
    √ always center color scale at zero and stretch to [-1, 1]
    √ Overlapping Windows and interactive time lag visualization
    √ revive visualization of current time lag (one window marker at the base window position, )
    √ Caching of min/max values of the data set
    √ Saving images
√ Vorarbeit Signifikanztests
    √ Standardfehler für Korrlationswerte
    √ Java Implementierung für T-Test
√ Vorarbeit Beschleunigte Cross-Correlation Berechnung
    √ Vorberechnungen wiederverwendbarer Terme auf Zeitreihen
√ Datenmodell
    √ Persistierung von Zeitreihen

------------------------------------------------------------------------------------------------------------------------
Milestone 13.6. 
------------------------------------------------------------------------------------------------------------------------

√ runtime prediction
    √ extrapolate from already spent time and percentage of columns already finished

√ Neuer Cross-Correlation Algorithmus: Beschleunigung, Speichereffizienz 
    √ Column-wise matrix computation (no entire matrices as partial results) 
    √ Lag Window Caching
    √ Parallelisierung 
        bei 3-4 threads naiv etwa genauso schnell wie mit Lag-Cache.
        unparallelisiert: 1000x1000 etwa 5,5h
                          1x1000 |w| = 200 ∆ = 1 |tau| ≤ 100
                            Raw data computation: 1172419 (~20min)
                            Aggregation: 37086 (37sec = 2,5%) ratio scheint stabil, solange die eingabesets groß sind.
        parallelisiert mit lag cache 1000x1000 std param ca. 1h-1.5h
        Prinzip: jeder Thread bekommt eine etwa gleich große Partition von Set A und eine von Set B.
        Für jede Spalte werden jetzt zunächst (parallel) die Werte der Lag Windows vorberechnet.
        Eine Barriere sorgt dafür, dass erst nach Abschluss der Vorberechnung in allen Threads die eigentliche Berechnung beginnt.
        Hierzu werden arbeitet jeder Thread seine Partition von Set A ab, indem er die entsprechenden Basis-Fenster mit den Lag-Fenstern
        kombiniert. Da auf den Lag-Cache nur lesend zugegriffen wird, entstehen keine Probleme.
        Eine Weitere Barriere synchronisiert alle Threads, bevor die Vorberechnung der Werte für die nächste Spalte beginnt. 
    
    
√ Entscheidungshilfe Symmetrien für Norbert vorbereiten 
    √ Eingabezeitreihen
    √ Korrelogramm AB, BA
    √ Spiegelung
    √ (Scherung?)

(√) Clustering along time steps:
    fixed k is not suitable (for three, the result is probably not much different from a median/iqr plot)
    but sometimes, forks show up, so the approach is promising.
    one problem is that the time series are so long, which makes the forks at very tiny time steps somehow meaningless (?) or at least hard to read.

(√) Signifikanzniveau-Schätzer kann in Sachs ("Angewandte Statistik") gefunden werden. Hat in etwa die Form
    t = c sqrt((N-1)/sqrt(1-c^2))
    tt = x + (x^2+x)/(4N)
    Signifikanzniveau wird aber nur einmal berechnet, braucht man nicht

√ increase render speed
    √ clipping
    √ avoid rendering on subpixel scale (clipping in resolution dimension)
    √ avoiding moire effects without drawing additional borders (but larger rects) is much faster.
    √ avoiding rerender for highlighting the currently active window by using the scene graph
    
√ Zählung signifikanter Korrelationswerte in einer Zelle
    √ Radio Button Switcher 
    √ Unipolare Farbskala
    
------------------------------------------------------------------------------------------------------------------------
Milestone 11.7. 
------------------------------------------------------------------------------------------------------------------------

> Hot Topics: 
    - multiple significance tests at once (e.g. 10%,5%,1%) (because a change would require the entire data volume to be recomputed) 

    - Precision. Can be used to save memory, increase computation speed, etc.
    Median computation:
        Apache Commons as competitor (approximately 2x faster than standard sort algorithm, even with having to add all the elements first!)
        Counting sort is as fast as commons for 6 digits precision
        Faster by a factor of 10x-20x for 3 digits precision
        Slower by a factor of 2x for 7 digits precision
    Test run for int inputSets = 100; int setSize = 1000001; 
    Commons: 2835
    Counting: 182
    Errors produced by counting sort (4 decimals precision):
    min: -9.771588930229669E-5
    max: -6.932158775896369E-7
    sdv: 2.742360311702581E-5

- visualize current time lag
    - place a tick at the current mouse position?
    - place a marker in the time series visualization?
    - draw a second window, one blue, one green? to show base and lag window?
    - yellow window is net very well legible

- computation input
    - autoconvert from data points to years (window size, min/max lag, etc.) 
- multi-view visualization
    (√?) Einseitige Tests/Zweiseitiger Test (Prozentsatz signifikanter Zellen)
    - Flicker, Slider for blending overlaid views
    √ Filter cells by different properties (% sig, mean, median, iqr, std dev)

- Docking Windows ?
    - only tabs->windows and vice versa: especially time series

- performance and responsiveness
    - compute cell dist only when visible
    - aggregate bins only on compute
    - feedback on what's happening: blocking all?
    - time series selector is very slow
    - when rectangle zooming into time series chart, high-res corros can cause a real lot of rendering delay (almost like crash)
            reason is probably that axesRanges are not set first. the resolution clipping cannot be used for small areas but the entire corro is rendered 
            (although most parts invisible) which is very slow. one frequent error is to manipulate axes ranges directly instead of setting the axesranges property in the canvas chart!
            
- ts vis
	- min/max (as dotted series) median (as solid line), quartiles (as filled band)
	    - maybe mean 
	    - fast median computation?
	- polyline rendering to avoid render fragments where line segments overlap
	
- memory management
    - closing main windows doesn't free their memory
    
- minors
    - select the new computation in the table view after computation
    - when switching tabs, the legend tooltip moves but doesn't update its label

- matrix computation
    - on the fly computation of the matrix results
        - is slow for 1000x1000 time series (and, in fact slows down everything, even if the cell distribution view is not visible)
        - can be avoided by saving the correlograms for each cell (at highest resolution?) ~ 50 values per cell, using a "continous compression",
          e.g. saving the offset of the first bin and the consecutively saving the bin sizes (possibly with zeros) should keep us below 40 mb.
        - as a last resort, reloading the data from the netCDF file might be an option, if we're running out of main memory
    - precomputing different significance thresholds
    - precomputation of matrix values
        - there are already two parameters that influence the density of the precomp grid: lag step and window delta. ignoring both might lead
        to intolerable loss of time. 


------------------------------------------------------------------------------------------------------------------------

Fixlist:
    null values (?) in ts histo vis
    hinton with 1D
    ts scroll bars
Testlist:
    serialization netcdf
    histogram binning


Ausstehend
    Stützstelleninterpolation auf der X-Achse
    Markierung von Computation-Input-Bereichen in der Zeitreihenvisualisierung
        - letztes Punktpaar eines Ensembles wird manchmal fälschlicherweise flat gezeichnet (slope 0)
        - letzte Spalte einer Korrelationsmatrix wird nicht in der Zeitreihenansicht visualisiert
        - beides lässt sich im anticorrelated Datensatz reproduzieren
        - Rückwärtszeitachsen verursachen Verwirrung in den Scrollbalken (TS und Corro)
        - Resizing the upper half of the UI when the TS tab is NOT active leaves the TS Chart in an ill size state when activating it
    Aggregation von nicht-rechteckigen Matrixbereichen
    Hochauflösender Grafikexport

------------------------------------------------------------------------------------------------------------------------
Auf der Agenda
---------------------------- --------------------------------------------------------------------------------------------

- Horizontal space aggregation: how to deal with insufficient number of pixels?
    √ scroll bars -> sensitize user for the problem
    √ binning: aggregate data points in bins of user choosen size
- Depth space aggregation: how to deal with a large number of time series?
    - plot distributions instead of values
        - percentile curves
        - shaded density
    - let the user choose a mode e.g.
        - plot all time series
        - plot only median curve
        - plot median and iqr curves

√ uncertainty in correlograms via column width
 
- Rendering-Beschleunigung: 
    - Umlagerung der Renderlast auf analytische Last (parallelisierbar?)
        - Gradienten/histogramme vorberechnen und die entsprechenden Farben ableiten statt durch transparentes overplotting zu erzeugen
    - Grafikkarte?!
    
- Integration der Signifikanzanteile als Maß der Unsicherheit in das Korrelogramm

- Drilling into (aggregated) correlogram cells
    √ plot distribution of correlation values
    X select a range of correlation values and highlight the time series that created these correlations within this window and lag
        (Norbert) welche Zeitreihe aus set A und welche aus set B dabei die jeweiligen Korrelationswerte erzeugt haben, ist unwichtig
    √ Navigation/View
        √ reset ts view when reseting corro view
  
√ Eingabe: zwei Dateien. 
    - Unterschiedliche Skalen für beide Datensets? zwei Achsen?
    - z-Transformation der Zeitreihen -> massive overplotting

- Automatically test for normally distributed values in cells
    - several test methods exist.
    
- saving vectorized output

- perf: dass nur sichtbare Views aktualisiert werden, dass die Zeitreihenansicht nur aktualisiert wird, wenn man zu ihr wechselt; Bis jetzt wird bei jeder Änderung der Zeitreihenauswahl das Binning nachberechnet und auch noch mal geplottet. Dein Vorschlag war, dass erst nach dem der Benutzer „berechnen“ geklickt hat, zu machen, aber ich denke, es wäre schon gut, wenn man sehen könnte, was für Zeitreihen man gewählt hat, bevor man die Berechnung startet. 


------------------------------------------------------------------------------------------------------------------------
Refactoring
------------------------------------------------------------------------------------------------------------------------

- Andrea fragen wegen Generalisierung der Matrix

- The differentiation between time series and complex sequence is based on the DFT algorithms not in use anymore

------------------------------------------------------------------------------------------------------------------------
Verworfene Ideen
------------------------------------------------------------------------------------------------------------------------

X Andere Farbskalen (tripolar? cf. matlab/colorbrewer)
    mapping uncertainty to glyph size does the job quite well
    
------------------------------------------------------------------------------------------------------------------------
Langfristige Ideen 
------------------------------------------------------------------------------------------------------------------------

- auto-choose number of random time series based on convergence of the matrix (or even cells?)

- doing the computations with floats rather than doubles would allow halving the memory usage

- clustering in windows? median/scheme time series for clusters?

Unsicherheits-Visualisierung
    - Texturen (z.B. speckles) lassen eine genauere Trennung der "Farbanteile" zu
    - Blurry Grid for visualization of uncertainty
    √ column width for visualization of uncertainty (for very narrow columns this is almost binary or should again convert to transparency)
    
Modularisierung des Maßes (der Fensteroperation). Neben dem Pearson-Korrelationskoeffizient würde auch Sinn machen:
    - Spearman Korrelation
    - Event Synchronisation
    - Mutual Information


3D Darstellung
    - correlogramme als volumen -> median und quantile sind flächen.
