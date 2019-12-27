var gulp = require('gulp');
var sass = require('gulp-sass');
var bower = require('gulp-bower');
var del = require('del');
var autoprefixer = require('gulp-autoprefixer');
var merge = require('merge2');
var rev = require('gulp-rev');
var revReplace = require('gulp-rev-replace');
var odeSassImports = require('gulp-ode-sass-imports');
var mergeJson = require('gulp-merge-json');
var flatmap = require("gulp-flatmap");
var postcss = require('postcss');
var gulppostcss = require('gulp-postcss');
var rename = require("gulp-rename");

var themeConf = require('./theme-conf').conf;

var sourceDependency = [];

function widgetsSources() {
    var sources = [];
    for (var widget in themeConf.dependencies.widgets) {
        sources.push('./bower_components/' + widget + '/**/*');
    }
    return sources;
}

function themesSources() {
    var sources = [];
    for (var theme in themeConf.dependencies.themes) {
        sources.push('./bower_components/' + theme + '/**/*');
    }
    return sources;
}

gulp.task('clean', function () {
    var delPaths = ['./assets/themes/*', './bower_components'];
    themeConf.overriding.forEach((overriding) => {
        delPaths.push('!./assets/themes/' + overriding.child);
        delPaths.push('./assets/themes/' + overriding.child + '/*');
        delPaths.push('!./assets/themes/' + overriding.child + '/override*');
    })
    return del(delPaths);
});

gulp.task('bower', ['clean'], () => {
    return bower({ cwd: '.' });
});

gulp.task('update', ['bower'], function () {
    var themes = gulp.src(themesSources(), { base: './bower_components' })
        .pipe(gulp.dest('./assets/themes'));

    var widgets = gulp.src(widgetsSources(), { base: './bower_components' })
        .pipe(gulp.dest('./assets/widgets'));

    return merge([themes, widgets]);
});

gulp.task('fill-theme', sourceDependency, function () {
    var streams = [];
    themeConf.overriding.forEach((theme) => {
        streams.push(
            gulp.src('./assets/themes/' + theme.parent + '/**/*')
                .pipe(gulp.dest('./assets/themes/' + theme.child))
        );
    })
    return merge(streams);
});

gulp.task('version-fonts', ['fill-theme'], function () {
    var fonts = ['./assets/themes/generic-icons/**/*.woff', './assets/themes/generic-icons/**/*.ttf', './assets/themes/generic-icons/**/*.svg'];
    return gulp.src(fonts)
        .pipe(rev())
        .pipe(gulp.dest('./assets/themes/generic-icons'))
        .pipe(rev.manifest())
        .pipe(gulp.dest('./'));
});

gulp.task('copy-local', function () {
    var streams = [];
    for (var theme in themeConf.dependencies.themes) {
        streams.push(
            gulp.src(themeConf.dependencies.themes[theme], { base: '../' })
                .pipe(gulp.dest('./assets/themes'))
        )
    }

    for (var widget in themeConf.dependencies.widgets) {
        streams.push(
            gulp.src(themeConf.dependencies.widgets[widget], { base: '../' })
                .pipe(gulp.dest('./assets/widgets'))
        )
    }

    return merge(streams);
});

gulp.task('override-theme', ['version-fonts'], function () {
    var overrides = ['img', 'js', 'fonts', 'template', 'css'/*, 'i18n'*/];
    var streams = [];

    streams.push(
        gulp.src(['./theme-conf.js'])
            .pipe(gulp.dest('./assets'))
    );

    themeConf.overriding.forEach((overriding) => {
        overrides.forEach((override) => {
            streams.push(
                gulp.src(['./assets/themes/' + overriding.child + '/overrides/' + override + '/**/*'])
                    .pipe(gulp.dest('./assets/themes/' + overriding.child + '/' + override))
            );
        });

        // Merging i18n key by key
        // console.log("Merging i18n...");
        // console.log("child theme:", overriding.child);
        gulp.src('./assets/themes/' + overriding.child + '/overrides/i18n/*')
            .pipe(flatmap(function (stream, file) {
                const appname = file.path.replace(file.base, "");
                // console.log("App", appname/*, file.base*/, file.path);
                gulp.src('./assets/themes/' + overriding.child + '/overrides/i18n/' + appname + '/*')
                    .pipe(flatmap(function (streamLang, fileLang) {
                        const langname = fileLang.path.replace(fileLang.base, "");
                        // console.log("    Lang", langname/*, fileLang.base*/, fileLang.path);
                        // console.log("    => Merge :",
                        //     './assets/themes/' + overriding.child + '/i18n/' + appname + '/' + langname,
                        //     '\n               ./assets/themes/' + overriding.child + '/overrides/i18n/' + appname + '/' + langname
                        // );
                        gulp.src([
                            './assets/themes/' + overriding.child + '/i18n/' + appname + '/' + langname,
                            './assets/themes/' + overriding.child + '/overrides/i18n/' + appname + '/' + langname
                        ])
                            .pipe(mergeJson({
                                fileName: langname
                            }))
                            .pipe(gulp.dest('./assets/themes/' + overriding.child + '/i18n/' + appname + '/'))
                        return streamLang;
                    }))
                return stream;
            }))
        // console.log("Merging i18n ok.");
        // End merging i18n key by key


        // Merge template/override.json from parent and child theme
        gulp.src([
            './assets/themes/' + overriding.parent + '/template/override.json',
            './assets/themes/' + overriding.child + '/overrides/template/override.json'
        ])
            .pipe(mergeJson({
                fileName: 'override.json',
            }))
            .pipe(gulp.dest('./assets/themes/' + overriding.child + '/template'));

        streams.push(
            gulp.src(['./assets/themes/' + overriding.parent + '/css/modules/_modules.scss'])
                .pipe(odeSassImports(overriding.parent))
                .pipe(gulp.dest('./assets/themes/' + overriding.parent + '/css/modules'))
        );

        streams.push(
            gulp.src(['./assets/themes/' + overriding.child + '/css/modules/_modules.scss'])
                .pipe(odeSassImports(overriding.parent))
                .pipe(gulp.dest('./assets/themes/' + overriding.child + '/css/modules'))
        );
    });

    streams.push(
        gulp.src('./mods/**/view/*.html')
            .pipe(odeSassImports())
            .pipe(gulp.dest('./mods'))
    );

    return merge(streams);
});

gulp.task('compile-sass', ['override-theme'], function () {
    var streams = [];

    themeConf.overriding.forEach((overriding) => {
        overriding.skins.forEach((skin) => {
            var stream = gulp.src('./assets/themes/' + overriding.child + '/skins/' + skin + '/theme.scss')
            .pipe(sass({ outputStyle: 'compressed' }))
            .pipe(autoprefixer())
            .pipe(revReplace({ manifest: gulp.src("./rev-manifest.json") }))
            .pipe(gulp.dest('./assets/themes/' + overriding.child + '/skins/' + skin));
            if(themeConf.emitWrapper){
                stream = stream.pipe(gulppostcss(function(file) {
                    return {
                        plugins: [
                            postcss.plugin('postcss-prepend-selector', function (opts) {
                                opts = opts || { selector: ".ode-theme-v1"};
                                return function (css) {
                                    css.walkRules(function (rule) {
                                        rule.selectors = rule.selectors.map( function (selector) {
                                            function removeBody(sel){
                                                if(sel && sel.indexOf("body")>-1){
                                                    sel = sel.replace(/^\s*body([\.\s])/,"$1").replace(/\s+body\s*$/," ")
                                                    sel = sel.replace(/([^a-zA-Z0-9\.\#])body([^a-zA-Z0-9])/, "$1$2")
                                                    if(sel.trim()=="body"){
                                                        sel = "";
                                                    }
                                                }
                                                return sel;
                                            }
                                            if(/^([0-9]*[.])?[0-9]+\%$|^from$|^to$/.test(selector)) {
                                                // This is part of a keyframe
                                                return removeBody(selector);
                                            }
                            
                                            if (selector.startsWith(opts.selector.trim())) {
                                                return removeBody(selector);
                                            }
                            
                                            return opts.selector + " " +removeBody(selector);
                                        });
                                    });
                                };
                            })
                        ],
                        options: {}
                    }
                }))
                .pipe(rename('wrapped.theme.css'))
                .pipe(gulp.dest('./assets/themes/' + overriding.child + '/skins/' + skin))
            }
            streams.push(stream);
        })
    });

    return merge(streams);
});


gulp.task('build-local', function () {
    sourceDependency.push('copy-local')
    return gulp.start('compile-sass')
});
gulp.task('build', function () {
    sourceDependency.push('update');
    return gulp.start('compile-sass');
});
